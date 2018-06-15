package org.broadinstitute.dsde.workbench.service

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.service.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import org.broadinstitute.dsde.workbench.fixture.Method
import org.broadinstitute.dsde.workbench.fixture.MethodData.SimpleMethod
import org.broadinstitute.dsde.workbench.service.Sam.user.UserStatusDetails
import org.broadinstitute.dsde.workbench.service.util.Retry
import spray.json.{DefaultJsonProtocol, _}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import OrchestrationModel._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.Cookie
import org.broadinstitute.dsde.workbench.service.test.RandomUtil

trait Orchestration extends RestClient with LazyLogging with SprayJsonSupport with DefaultJsonProtocol with RandomUtil {

  def responseAsList[T](response: String): List[Map[String, T]] = {
    mapper.readValue(response, classOf[List[Map[String, T]]])
  }

  private def apiUrl(s: String) = {
    ServiceTestConfig.FireCloud.orchApiUrl + s
  }

  object billing {

    object BillingProjectRole extends Enumeration {
      type BillingProjectRole = Value
      val User = Value("User")
      val Owner = Value("Owner")
    }

    object BillingProjectStatus extends Enumeration {
      type BillingProjectStatus = Value
      val Creating = Value("Creating")
      val Ready = Value("Ready")
      val Error = Value("Error")

      val terminalStates = List(Ready, Error)
      def isTerminal(status: BillingProjectStatus): Boolean = terminalStates.contains(status)
      def isActive(status: BillingProjectStatus): Boolean = !isTerminal(status)
    }

    import BillingProjectRole._
    import BillingProjectStatus._

    case class BillingProject(projectName: String, role: BillingProjectRole, creationStatus: BillingProjectStatus)

    def addUserToBillingProject(projectName: String, email: String, role: BillingProjectRole)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding user to billing project: $projectName $email ${role.toString}")
      putRequest(apiUrl(s"api/billing/$projectName/${role.toString}/$email"))
    }

    def removeUserFromBillingProject(projectName: String, email: String, role: BillingProjectRole)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing user from billing project: $projectName $email ${role.toString}")
      deleteRequest(apiUrl(s"api/billing/$projectName/${role.toString}/$email"))
    }

    def addGoogleRoleToBillingProjectUser(projectName: String, email: String, googleRole: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding google role $googleRole to user $email in billing project $projectName")
      putRequest(apiUrl(s"api/billing/$projectName/googleRole/$googleRole/$email"))
    }

    def removeGoogleRoleFromBillingProjectUser(projectName: String, email: String, googleRole: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing google role $googleRole from user $email in billing project $projectName")
      deleteRequest(apiUrl(s"api/billing/$projectName/googleRole/$googleRole/$email"))
    }

    def createBillingProject(projectName: String, billingAccount: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Creating billing project: $projectName $billingAccount, with start time of ${System.currentTimeMillis}")
      postRequest(apiUrl("api/billing"), Map("projectName" -> projectName, "billingAccount" -> billingAccount))

      Retry.retry(10.seconds, 20.minutes)({
        Try(responseAsList(parseResponse(getRequest(apiUrl("api/profile/billing"))))) match {
          case Success(response) => response.map { p =>
            BillingProject(p("projectName"), BillingProjectRole.withName(p("role")), BillingProjectStatus.withName(p("creationStatus")))
          }.find(p => p.projectName == projectName && BillingProjectStatus.isTerminal(p.creationStatus))
          case Failure(t) => logger.info(s"Billing project creation encountered an error: ${t.getStackTrace}"); None
        }
      }) match {
        case Some(BillingProject(name, _, BillingProjectStatus.Ready)) =>
          logger.info(s"Finished creating billing project: $name $billingAccount, with completion time of ${System.currentTimeMillis}")
        case Some(BillingProject(name, _, _)) =>
          logger.info(s"Encountered an error creating billing project: $name $billingAccount, with final attempt at ${System.currentTimeMillis}")
          throw new Exception("Billing project creation encountered an error")
        case None => throw new Exception("Billing project creation did not complete")
      }
    }

  }

  object duos {
    def researchPurposeQuery(DS: Seq[String] = Seq.empty,
                             NMDS: Boolean = false,
                             NCTRL: Boolean = false,
                             NAGR: Boolean = false,
                             POA: Boolean = false,
                             NCU: Boolean = false,
                             prefix: String = "")(implicit token: AuthToken): String = {
      val request = Map(
        "DS" -> DS,
        "NMDS" -> NMDS,
        "NCTRL" -> NCTRL,
        "NAGR" -> NAGR,
        "POA" -> POA,
        "NCU" -> NCU,
        "prefix" -> prefix
      )
      postRequest(apiUrl("duos/researchPurposeQuery"), request)
    }
  }

  object groups {

    object GroupRole extends Enumeration {
      type GroupRole = Value
      val Member = Value("member")
      val Admin = Value("admin")
    }
    import GroupRole._

    def addUserToGroup(groupName: String, email: String, role: GroupRole)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding user to group: $groupName $email ${role.toString}")
      putRequest(apiUrl(s"api/groups/$groupName/${role.toString}/$email"))
    }

    def create(groupName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Creating group: $groupName")
      postRequest(apiUrl(s"api/groups/$groupName"))
    }

    def delete(groupName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting group: $groupName")
      deleteRequest(apiUrl(s"api/groups/$groupName"))
    }

    def removeUserFromGroup(groupName: String, email: String, role: GroupRole)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing user from group: $groupName $email ${role.toString}")
      deleteRequest(apiUrl(s"api/groups/$groupName/${role.toString}/$email"))
    }

    def getGroup(groupName: String)(implicit token: AuthToken): ManagedGroupWithMembers = {
      parseResponseAs[ManagedGroupWithMembers](getRequest(apiUrl(s"api/groups/$groupName")))
    }
  }

  /*
   *  Workspace requests
   */

  object workspaces {

    def create(namespace: String, name: String, authDomain: Set[String] = Set.empty)
              (implicit token: AuthToken): Unit = {
      logger.info(s"Creating workspace: $namespace/$name authDomain: $authDomain")

      val authDomainGroups = authDomain.map(a => Map("membersGroupName" -> a))

      val request = Map("namespace" -> namespace, "name" -> name,
        "attributes" -> Map.empty, "authorizationDomain" -> authDomainGroups)

      postRequest(apiUrl(s"api/workspaces"), request)
    }

    def clone(originNamespace: String, originName: String, cloneNamespace: String, cloneName: String, authDomain: Set[String] = Set.empty, membersGroupName: String, attributes: Map[String, String])
             (implicit token: AuthToken): Unit = {
      logger.info(s"Copying workspace: $originNamespace/$originName authDomain: $authDomain")

      val authDomainGroups = authDomain.map(a => Map("membersGroupName" -> a))

      val request = Map("namespace" -> cloneNamespace, "name" -> cloneName,
        "attributes" -> Map.empty, "authorizationDomain" -> authDomainGroups)

      postRequest(apiUrl(s"api/workspaces/$originNamespace/$originName/clone"), request)
    }

    def delete(namespace: String, name: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting workspace: $namespace/$name")
      deleteRequest(apiUrl(s"api/workspaces/$namespace/$name"))
    }

    def updateAcl(namespace: String, name: String, email: String, accessLevel: WorkspaceAccessLevel, canShare: Option[Boolean], canCompute: Option[Boolean])(implicit token: AuthToken): Unit = {
      updateAcl(namespace, name, List(AclEntry(email, accessLevel, canCompute, canShare)))
    }

    def updateAcl(namespace: String, name: String, aclEntries: List[AclEntry] = List())(implicit token: AuthToken): Unit = {
      logger.info(s"Updating ACLs for workspace: $namespace/$name $aclEntries")
      patchRequest(apiUrl(s"api/workspaces/$namespace/$name/acl"),
        aclEntries.map{ e => e.toMap })
    }

    /*
    * The values in the attributes map should be either String or Seq[String]. An Either is not used because the object
    * mapper that converts scala to json represents the either in the json string.
    */
    def setAttributes(namespace: String, name: String, attributes: Map[String, Any])(implicit token: AuthToken): Unit = {
      logger.info(s"Setting attributes for workspace: $namespace/$name $attributes")
      patchRequest(apiUrl(s"api/workspaces/$namespace/$name/setAttributes"), attributes)
    }

    /**
      * Sometimes access control takes a little while to propagate in google land, use this function to wait
      * for anything where bucket access is required. Specifically the launch workflow button is disabled
      * when checkBucketReadAccess returns false.
      *
      * @param workspaceNamespace
      * @param workspaceName
      * @param token
      */
    def waitForBucketReadAccess(workspaceNamespace: String, workspaceName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Bucket read access checking on workspace: $workspaceNamespace/$workspaceName")
      Retry.retry(10.seconds, 10.minutes)({
        val response = getRequest(apiUrl(s"api/workspaces/$workspaceNamespace/$workspaceName/checkBucketReadAccess"))
        if(response.status.isSuccess()) Some("done") else None
      }) match {
        case None => throw new Exception(s"workspace $workspaceNamespace/$workspaceName bucket did not become readable")
        case Some(_) => logger.info(s"Bucket read access check passed: workspace $workspaceNamespace/$workspaceName bucket readable")
      }
    }
  }


  /*
   *  Library requests
   */

  object library {
    def setLibraryAttributes(ns: String, name: String, attributes: Map[String, Any])(implicit token: AuthToken): String = {
      logger.info(s"Setting library attributes for workspace: $ns/$name $attributes")
      putRequest(apiUrl(s"api/library/$ns/$name/metadata"), attributes)
    }

    def setDiscoverableGroups(ns: String, name: String, groupNames: List[String])(implicit token: AuthToken): String = {
      logger.info(s"Setting discoverable groups for workspace: $ns/$name $groupNames")
      putRequest(apiUrl(s"api/library/$ns/$name/discoverableGroups"), groupNames)
    }

    def publishWorkspace(ns: String, name: String)(implicit token: AuthToken): String = {
      logger.info(s"Publishing workspace: $ns/$name")
      postRequest(apiUrl(s"api/library/$ns/$name/published"))
    }

    def unpublishWorkspace(ns: String, name: String)(implicit token: AuthToken): String = {
      logger.info(s"Unpublishing workspace: $ns/$name")
      deleteRequest(apiUrl(s"api/library/$ns/$name/published"))
    }

    def duosAutocomplete(query: String)(implicit token: AuthToken): String = {
      logger.info(s"DUOS Autocomplete: $query")
      parseResponse(getRequest(apiUrl(s"duos/autocomplete/$query")))
    }

    def getDiscoverableGroups(ns: String, wName: String)(implicit token: AuthToken): Seq[String] = {
      logger.info(s"Getting discoverable groups for workspace: $ns/$wName")
      parseResponseAs[Seq[String]](getRequest(apiUrl(s"api/library/$ns/$wName/discoverableGroups")))
    }
  }

  /*
   *  Method Configurations requests
   */

  object methodConfigurations {

    //    This only works for method configs, but not methods
    def copyMethodConfigFromMethodRepo(ns: String, wsName: String, configurationNamespace: String, configurationName: String, configurationSnapshotId: Int, destinationNamespace: String, destinationName: String)(implicit token: AuthToken): String = {
      logger.info(s"Copying method config from method repo: $ns/$wsName config: $configurationNamespace/$configurationName $configurationSnapshotId destination: $destinationNamespace/$destinationName")
      postRequest(apiUrl(s"api/workspaces/$ns/$wsName/method_configs/copyFromMethodRepo"),
        Map("configurationNamespace" -> configurationNamespace, "configurationName" -> configurationName, "configurationSnapshotId" -> configurationSnapshotId, "destinationNamespace" -> destinationNamespace, "destinationName" -> destinationName))
    }

    def createMethodConfigInWorkspace(wsNs: String, wsName: String, method:Method,
                                      configNamespace: String, configName: String, methodConfigVersion: Int,
                                      inputs: Map[String, String], outputs: Map[String, String],
                                      rootEntityType: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Creating method config: $wsNs/$wsName $methodConfigVersion method: ${method.methodNamespace}/${method.methodName} config: $configNamespace/$configName")
      postRequest(apiUrl(s"api/workspaces/$wsNs/$wsName/methodconfigs"),
        Map("deleted" -> false,
          "inputs" -> inputs,
          "methodConfigVersion" -> methodConfigVersion,
          "methodRepoMethod" -> method.methodRepoInfo,
          "namespace" -> configNamespace,
          "name" -> configName,
          "outputs" -> outputs,
          "prerequisites" -> Map(),
          "rootEntityType" -> rootEntityType)
      )
    }

    def createMethodConfig(methodConfigData: Map[String,Any])(implicit token: AuthToken): String = {
      logger.info(s"Adding a method config")
      postRequest(apiUrl(s"api/configurations"), methodConfigData)
    }

    def getMethodConfigPermission(configNamespace: String)(implicit token: AuthToken): String = {
      logger.info(s"Getting permissions for method config: $configNamespace")
      parseResponse(getRequest(apiUrl(s"api/configurations/$configNamespace/permissions")))
    }
    def setMethodConfigPermission(configNamespace: String, configName: String, configSnapshotId: Int, user: String, role: String)(implicit token: AuthToken): String = {
      logger.info(s"Setting permissions for method config: $configNamespace/$configName/$configSnapshotId and user: $user to role: $role")
      postRequest(apiUrl(s"api/configurations/$configNamespace/$configName/$configSnapshotId/permissions"),
        Seq(Map("user" -> user,
        "role" -> role))
      )
    }
  }

  object methods {
    def createMethod(testname:String, method:Method, numSnapshots: Int = 1)
                    (implicit token: AuthToken): String = {
      val methodName = uuidWithPrefix(testname)
      for (_ <- 1 to numSnapshots)
        createMethod(SimpleMethod.creationAttributes + ("name"->methodName))
      methodName
    }

    def createMethod(methodData: Map[String,Any])(implicit token: AuthToken): Unit = {
      logger.info(s"Adding a method.")
      postRequest(apiUrl(s"api/methods"), methodData)
    }

    def redact(method: Method)(implicit token: AuthToken): Unit = {
      redact(method.methodNamespace, method.methodName, method.snapshotId)
    }

    def redact(ns: String, name: String, snapshotId: Int)(implicit token: AuthToken): Unit = {
      logger.info(s"Redacting method: $ns/$name:$snapshotId")
      deleteRequest(apiUrl(s"api/methods/$ns/$name/$snapshotId"))
    }

    def getMethodPermissions(ns: String, name: String, snapshotId: Int)(implicit token: AuthToken): String = {
      logger.info(s"Getting method permissions for $ns / $name")
      parseResponse(getRequest(apiUrl(s"api/methods/$ns/$name/$snapshotId/permissions")))
    }

    def setMethodPermissions(ns: String, name: String, snapshotId: Int, userId: String, role: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Setting method permissions for $ns / $name")
      val request = Seq(Map("user" -> userId, "role" -> role))
      postRequest(apiUrl(s"api/methods/$ns/$name/$snapshotId/permissions"), request)
    }
  }

  /*
   *  NIH requests
   */
  object NIH {

    case class NihStatus(linkedNihUsername: Option[String] = None,
                         datasetPermissions: Set[NihDatasetPermission],
                         linkExpireTime: Option[Long] = None)

    case class NihDatasetPermission(name: String, authorized: Boolean)

    implicit val impNihDatasetPermission = jsonFormat2(NihDatasetPermission)
    implicit val impNihStatus = jsonFormat3(NihStatus.apply)

    def addUserInNIH(jwt: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding user to NIH whitelist: $jwt")
      postRequest(apiUrl(s"/api/nih/callback"), Map("jwt" -> jwt))
    }

    def refreshUserInNIH(jwt: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Refreshing user's NIH status")
      NIH.addUserInNIH(jwt)
    }

    def syncWhitelistFull()(implicit token: AuthToken): Unit = {
      logger.info(s"Syncing whitelist (full)")
      postRequest(apiUrl("/sync_whitelist"))
    }

    def getUserNihStatus()(implicit token: AuthToken): NihStatus = {
      val response = getRequest(apiUrl("/api/nih/status"))
      parseResponse(response).parseJson.convertTo[NihStatus]
    }

  }
  /*
   *  Submissions requests
   */

  object submissions {
    def launchWorkflow(ns: String, wsName: String, methodConfigurationNamespace: String, methodConfigurationName: String, entityType: String, entityName: String, expression: String, useCallCache: Boolean, workflowFailureMode: String = "NoNewCalls")(implicit token: AuthToken): String = {
      logger.info(s"Creating a submission: $ns/$wsName config: $methodConfigurationNamespace/$methodConfigurationName")
      postRequest(apiUrl(s"api/workspaces/$ns/$wsName/submissions"),
        Map("methodConfigurationNamespace" -> methodConfigurationNamespace, "methodConfigurationName" -> methodConfigurationName, "entityType" -> entityType, "entityName" -> entityName, "expression" -> expression, "useCallCache" -> useCallCache, "workflowFailureMode" -> workflowFailureMode))
    }

  }

  object profile {
    // copied from firecloud-orchestration repo
    case class BasicProfile (
                              firstName: String,
                              lastName: String,
                              title: String,
                              contactEmail: Option[String],
                              institute: String,
                              institutionalProgram: String,
                              programLocationCity: String,
                              programLocationState: String,
                              programLocationCountry: String,
                              pi: String,
                              nonProfitStatus: String
                            )


    def registerUser(profile: BasicProfile)(implicit token: AuthToken): Unit = {
      profile.contactEmail match {
        case Some(email) => logger.info(s"Creating profile for user $email")
        case _ => logger.info("Creating user profile")
      }

      postRequest(apiUrl(s"register/profile"), profile)
    }

    def getUser()(implicit token: AuthToken): Map[String, String] = {
      parseResponseAs[Map[String, String]](getRequest(apiUrl(s"register/profile")))
    }

    def getUserBillingProjects()(implicit token: AuthToken): List[Map[String, String]] = {
      parseResponseAs[List[Map[String, String]]](getRequest(apiUrl(s"api/profile/billing")))
    }

    def getUserBillingProjectStatus(projectName: String)(implicit token: AuthToken): Map[String, String] = {
      parseResponseAs[Map[String, String]](getRequest(apiUrl(s"api/profile/billing/$projectName")))
    }
  }

  def importMetaData(ns: String, wsName: String, fileName: String, fileContent: String)(implicit token: AuthToken): String = {
    logger.info(s"Importing metadata: $ns/$wsName $fileName")
    postRequestWithMultipart(apiUrl(s"api/workspaces/$ns/$wsName/importEntities"), fileName, fileContent)
  }

  object trial {

    case class TrialProjects(unverified: Int,
                             errored: Int,
                             available: Int,
                             claimed: Int)

    case class TrialProjectReport(name: String,
                                  verified: Boolean,
                                  user: Option[UserStatusDetails],
                                  status: Option[String])

    private def checkUserStatusUpdate(userEmail: String, update: String, response: String): Unit = {
      val successfulResponseKeys = Seq("Success", "NoChangeRequired")

      response.parseJson.asJsObject.fields.map {
        case f@x if successfulResponseKeys.contains(f._1) =>
          logger.info(s"${f._1}: ${f._2.toString()}")
          return
        case f@y =>
          logger.error(s"${f._1}: ${f._2.toString()}")
          throw new Exception(s"Unable to $update trial user: $userEmail. Error message: $response")
      }
    }

    def enableUser(userEmail: String)(implicit token: AuthToken): Unit = {
      val enableResponse: String = postRequest(apiUrl("api/trial/manager/enable"), Seq(userEmail))

      checkUserStatusUpdate(userEmail, "enable", enableResponse)
    }

    def terminateUser(userEmail: String)(implicit token: AuthToken): Unit = {
      val terminateResponse: String = postRequest(apiUrl("api/trial/manager/terminate"), Seq(userEmail))

      checkUserStatusUpdate(userEmail, "terminate", terminateResponse)
    }

    def createTrialProjects(count: Int)(implicit token: AuthToken): Unit = {
      val trialProjects: TrialProjects = countTrialProjects()
      if (trialProjects.available < count) {
        postRequest(apiUrl(s"api/trial/manager/projects?operation=create&count=${count - trialProjects.available}"))
        Retry.retry(30.seconds, 20.minutes)({
          val report: TrialProjects = countTrialProjects()
          if (report.available >= count)
            Some(report)
          else
            None
        }) match {
          case Some(_) => logger.info("Finished creating free tier project")
          case None => throw new Exception("Free tier project creation did not complete")
        }
      }
      else {
        logger.info("Available free tier project(s) already exist")
        // No-op. We have at least one available project to claim.
      }
    }

    def countTrialProjects()(implicit token: AuthToken): TrialProjects = {
      logger.info(s"API post request: api/trial/manager/projects?operation=count")
      val response = postRequest(apiUrl(s"api/trial/manager/projects?operation=count"))
      implicit val impTrialProjectReport: RootJsonFormat[TrialProjects] = jsonFormat4(TrialProjects)
      val trialProjects: TrialProjects = response.parseJson.convertTo[TrialProjects]
      logger.info(s"Trial Projects Available: ${trialProjects.available}")
      trialProjects
    }

    def reportTrialProjects()(implicit token: AuthToken): Seq[TrialProjectReport] = {
      logger.info(s"API post request: api/trial/manager/projects?operation=report")
      val response = postRequest(apiUrl(s"api/trial/manager/projects?operation=report"))
      implicit val impUserStatusDetails: RootJsonFormat[UserStatusDetails] = jsonFormat2(UserStatusDetails)
      implicit val impTrialProjectReport: RootJsonFormat[TrialProjectReport] = jsonFormat4(TrialProjectReport)
      val trialProjectReports: Seq[TrialProjectReport] = response.parseJson.convertTo[Seq[TrialProjectReport]]
      logger.info(s"Current Trial Project Reports: ${trialProjectReports.map(_.name).mkString(", ")}")
      trialProjectReports
    }

  }

  object storage {
    case class ObjectMetadata(
                 bucket: String,
                 crc32c: String,
                 etag: String,
                 generation: String,
                 id: String,
                 md5Hash: Option[String],
                 mediaLink: String,
                 name: String,
                 size: String,
                 storageClass: String,
                 timeCreated: String,
                 updated: String,
                 contentDisposition: Option[String],
                 contentEncoding: Option[String],
                 contentType: Option[String],
                 estimatedCostUSD: Option[BigDecimal]
               )

    def getObjectMetadata(bucketName: String, objectKey: String)(implicit token: AuthToken): ObjectMetadata = {
      logger.info(s"API getObjectMetadata request: api/storage/$bucketName/$objectKey")
      implicit val impObjectMetadata: RootJsonFormat[ObjectMetadata] = jsonFormat16(ObjectMetadata)
      val response = getRequest(apiUrl(s"api/storage/$bucketName/$objectKey"))
      parseResponse(response).parseJson.convertTo[ObjectMetadata]
    }

    // returns an HttpResponse which may contain entity data, a redirect, or an http error
    def getObjectDownload(bucketName: String, objectKey: String)(implicit token: AuthToken): HttpResponse = {
      logger.info(s"API getObjectDownload request: cookie-authed/download/b/$bucketName/o/$objectKey")
      val fctokenCookie = Cookie("FCtoken",token.value)
      getRequest(apiUrl(s"cookie-authed/download/b/$bucketName/o/$objectKey"), List(fctokenCookie))
    }
  }

}
object Orchestration extends Orchestration

/**
  * Dictionary of access level values expected by the web service API.
  */
object WorkspaceAccessLevel extends Enumeration {
  type WorkspaceAccessLevel = Value
  val NoAccess = Value("NO ACCESS")
  val Owner = Value("OWNER")
  val Reader = Value("READER")
  val Writer = Value("WRITER")
}

case class AclEntry(email: String, accessLevel: WorkspaceAccessLevel, canShare: Option[Boolean] = None, canCompute: Option[Boolean] = None) {
  def toMap: Map[String,Any] = {
    val resp: Map[String, Any] = Map("email"->email, "accessLevel"->accessLevel.toString)
    val shared = canShare match {
      case Some(sh) => resp ++ Map("canShare"->sh)
      case None => resp
    }
    val compute = canCompute match {
      case Some(comp) => shared ++ Map("canCompute"->comp)
      case None => shared
    }
    compute
  }
}

object OrchestrationModel {
  import DefaultJsonProtocol._
  case class RawlsGroupShort(groupName: String, groupEmail: String)
  case class ManagedGroupWithMembers(membersGroup: RawlsGroupShort, adminsGroup: RawlsGroupShort, membersEmails: Seq[String], adminsEmails: Seq[String])

  implicit val RawlsGroupShortFormat = jsonFormat2(RawlsGroupShort)
  implicit val ManagedGroupWithMembersFormat = jsonFormat4(ManagedGroupWithMembers)
}
