package org.broadinstitute.dsde.workbench.service

import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{UserPool, _}
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.service.Sam.user.UserStatusDetails
import org.broadinstitute.dsde.workbench.service.SamModel._
import org.broadinstitute.dsde.workbench.service.SamModel.SamJsonSupport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import spray.json._
import spray.json.DefaultJsonProtocol._

/**
 * Sam API service client. This should only be used when Orchestration does
 * not provide a required endpoint. This should primarily be used for admin
 * functions.
 */
trait Sam extends RestClient with LazyLogging with ScalaFutures {

  val url = ServiceTestConfig.FireCloud.samApiUrl

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  def removePet(project: String, userInfo: UserStatusDetails): Unit =
    Sam.admin.deletePetServiceAccount(project, userInfo.userSubjectId)(UserPool.chooseAdmin.makeAuthToken())

  object admin {

    def deleteUser(subjectId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting user: $subjectId")
      deleteRequest(url + s"api/admin/user/$subjectId")
    }

    def doesUserExist(subjectId: String)(implicit token: AuthToken): Option[Boolean] =
      getRequest(url + s"api/admin/user/$subjectId").status match {
        case StatusCodes.OK       => Option(true)
        case StatusCodes.NotFound => Option(false)
        case _                    => None
      }

    def deletePetServiceAccount(project: String, userSubjectId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting pet service account in project $project for user $userSubjectId")
      deleteRequest(url + s"api/admin/user/$userSubjectId/petServiceAccount/$project")
    }
  }

}
object Sam extends Sam {

  object user {

    case class UserStatusDetails(userSubjectId: String, userEmail: String)

    case class UserStatus(userInfo: UserStatusDetails, enabled: Map[String, Boolean])

    case class UserStatusDiagnostics(enabled: Boolean, inAllUserGroup: Boolean, inGoogleProxyGroup: Boolean, adminEnabled: Option[Boolean], tosAccepted: Option[Boolean])

    case class UserStatusInfo(userSubjectId: String, userEmail: String, enabled: Boolean, adminEnabled: Option[Boolean])

    def status()(implicit token: AuthToken): Option[UserStatus] = {
      logger.info(s"Getting user registration status")
      parseResponseOption[UserStatus](getRequest(url + "register/user"))
    }

    def registerSelf()(implicit token: AuthToken): UserStatus = {
      logger.info("Registering user")
      val response = postRequest(url + "register/user/v2/self")
      implicit val impUserStatusDetails: RootJsonFormat[UserStatusDetails] = jsonFormat2(UserStatusDetails)
      implicit val impUserStatus: RootJsonFormat[UserStatus] = jsonFormat2(UserStatus)
      response.parseJson.convertTo[UserStatus]
    }

    def petServiceAccountEmail(project: String)(implicit token: AuthToken): WorkbenchEmail = {
      logger.info(s"Getting pet service account email")
      val petEmailStr = parseResponseAs[String](getRequest(url + s"api/google/user/petServiceAccount/$project"))
      logger.info(s"Getting pet service account email response: $petEmailStr")
      WorkbenchEmail(petEmailStr)
    }

    def petServiceAccountKey(project: String)(implicit token: AuthToken): String = {
      logger.info(s"Getting pet service account key")
      parseResponse(getRequest(url + s"api/google/user/petServiceAccount/$project/key"))
    }

    def arbitraryPetServiceAccountKey()(implicit token: AuthToken): String = {
      logger.info(s"Getting pet service account key")
      parseResponse(getRequest(url + s"api/google/user/petServiceAccount/key"))
    }

    def petServiceAccountToken(project: String, scopes: Set[String])(implicit token: AuthToken): String = {
      logger.info(s"Getting pet service account token")
      postRequest(url + s"api/google/user/petServiceAccount/$project/token", scopes)
    }

    def arbitraryPetServiceAccountToken(scopes: Set[String])(implicit token: AuthToken): String = {
      logger.info(s"Getting pet service account token")
      postRequest(url + s"api/google/user/petServiceAccount/token", scopes)
    }

    def proxyGroup(userEmail: String)(implicit token: AuthToken): WorkbenchEmail = {
      logger.info(s"Getting proxy group email")
      val proxyGroupEmailStr = parseResponseAs[String](getRequest(url + s"api/google/user/proxyGroup/$userEmail"))
      WorkbenchEmail(proxyGroupEmailStr)
    }

    def deletePetServiceAccountKey(project: String, keyId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting pet service account key $keyId in project $project")
      deleteRequest(url + s"api/google/user/petServiceAccount/$project/key/$keyId")
    }

    def getUserStatusDiagnostics()(implicit token: AuthToken): Option[UserStatusDiagnostics] = {
      logger.info(s"Getting user diagnostics")
      parseResponseOption[UserStatusDiagnostics](getRequest(url + s"register/user/v2/self/diagnostics"))
    }

    def getUserStatusInfo()(implicit token: AuthToken): Option[UserStatusInfo] = {
      logger.info(s"Getting user info")
      parseResponseOption[UserStatusInfo](getRequest(url + s"register/user/v2/self/info"))
    }

    def createGroup(groupName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Creating managed group with id: $groupName")
      postRequest(url + s"api/groups/v1/$groupName")
    }

    def deleteGroup(groupName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting managed group with id: $groupName")
      deleteRequest(url + s"api/groups/v1/$groupName")
    }

    def addUserToPolicy(groupName: String, policyName: String, memberEmail: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding $memberEmail to $policyName policy in $groupName")
      putRequest(url + s"api/groups/v1/$groupName/$policyName/$memberEmail")
    }

    def removeUserFromPolicy(groupName: String, policyName: String, memberEmail: String)(implicit
      token: AuthToken
    ): Unit = {
      logger.info(s"Removing $memberEmail from $policyName policy in $groupName")
      deleteRequest(url + s"api/groups/v1/$groupName/$policyName/$memberEmail")
    }

    def listResourcePolicies(resourceTypeName: String, resourceId: String)(implicit
      token: AuthToken
    ): Set[AccessPolicyResponseEntry] = {
      logger.info(s"Listing policies for $resourceId")
      val response = parseResponse(getRequest(url + s"api/resources/v1/$resourceTypeName/$resourceId/policies"))

      import spray.json.DefaultJsonProtocol._
      response.parseJson.convertTo[Set[AccessPolicyResponseEntry]](immSetFormat(AccessPolicyResponseEntryFormat))
    }

    def setPolicyMembers(groupName: String, policyName: String, memberEmails: Set[String])(implicit
      token: AuthToken
    ): Unit = {
      logger.info(s"Overwriting members in $policyName policy of $groupName")
      putRequest(url + s"api/groups/v1/$groupName/$policyName", memberEmails)
    }

    def createResource(resourceTypeName: String, resourceRequest: CreateResourceRequest)(implicit
      token: AuthToken
    ): Unit = {
      logger.info(s"Creating new resource $resourceRequest of type $resourceTypeName")
      postRequest(url + s"api/resources/v1/$resourceTypeName", resourceRequest)
    }

    def syncResourcePolicy(resourceTypeName: String, resourceId: String, policyName: String)(implicit
      token: AuthToken
    ): Unit = {
      logger.info(s"Synchronizing $policyName for $resourceId of type $resourceTypeName")
      postRequest(url + s"api/google/v1/resource/$resourceTypeName/$resourceId/$policyName/sync")
    }

    def makeResourcePolicyPublic(resourceTypeName: String, resourceId: String, policyName: String, isPublic: Boolean)(
      implicit token: AuthToken
    ): Unit = {
      logger.info(s"Making $policyName for $resourceId of type $resourceTypeName public")
      putRequest(url + s"api/resources/v1/$resourceTypeName/$resourceId/policies/$policyName/public", isPublic)
    }

    def deleteResource(resourceTypeName: String, resourceId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting resource $resourceId")
      deleteRequest(url + s"api/resources/v1/$resourceTypeName/$resourceId")
    }

    def addUserToResourcePolicy(resourceTypeName: String, resourceId: String, policyName: String, email: String)(
      implicit token: AuthToken
    ): Unit = {
      logger.info(s"Adding $email to $policyName for resource $resourceId")
      putRequest(url + s"api/resources/v1/$resourceTypeName/$resourceId/policies/$policyName/memberEmails/$email")
    }

    def getGroupEmail(groupName: String)(implicit token: AuthToken): WorkbenchEmail = {
      logger.info(s"Getting email for $groupName")
      val proxyGroupEmailStr = parseResponseAs[String](getRequest(url + s"api/groups/v1/$groupName"))
      WorkbenchEmail(proxyGroupEmailStr)
    }
  }

}

object SamModel {
  import spray.json.DefaultJsonProtocol

  object SamJsonSupport {
    import DefaultJsonProtocol._
    import org.broadinstitute.dsde.workbench.model.WorkbenchIdentityJsonSupport._

    implicit val AccessPolicyMembershipFormat = jsonFormat3(AccessPolicyMembership.apply)

    implicit val AccessPolicyResponseEntryFormat = jsonFormat3(AccessPolicyResponseEntry.apply)

    implicit val CreateResourceRequestFormat = jsonFormat3(CreateResourceRequest.apply)
  }

  final case class AccessPolicyMembership(memberEmails: Set[String], actions: Set[String], roles: Set[String])

  final case class AccessPolicyResponseEntry(policyName: String, policy: AccessPolicyMembership, email: WorkbenchEmail)

  final case class CreateResourceRequest(resourceId: String,
                                         policies: Map[String, AccessPolicyMembership],
                                         authDomain: Set[String]
  )
}
