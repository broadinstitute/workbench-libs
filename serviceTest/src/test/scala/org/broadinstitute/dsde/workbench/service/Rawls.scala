package org.broadinstitute.dsde.workbench.service

import com.fasterxml.jackson.databind.JsonNode
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.rawls.model.AttributeUpdateOperations.{
  AttributeUpdateOperation,
  AttributeUpdateOperationFormat
}
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import org.broadinstitute.dsde.workbench.fixture.Method
import org.broadinstitute.dsde.workbench.model.UserInfo
import org.broadinstitute.dsde.workbench.service.BillingProject.BillingProjectRole._
import spray.json.JsString

import scala.util.Try

trait Rawls extends RestClient with LazyLogging {

  val url: String = ServiceTestConfig.FireCloud.rawlsApiUrl

  def responseAsList[T](response: String): List[Map[String, T]] =
    mapper.readValue(response, classOf[List[Map[String, T]]])

  //noinspection RedundantBlock
  object billing {

    def createBillingProject(projectName: String, billingAccount: String, servicePerimeterOpt: Option[String] = None)(
      implicit token: AuthToken
    ): String = {
      logger.info(s"Creating billing project $projectName in billing account $billingAccount")
      val request = Map("projectName" -> projectName, "billingAccount" -> billingAccount) ++ servicePerimeterOpt.map(
        servicePerimeter => "servicePerimeter" -> servicePerimeter
      )
      postRequest(s"${url}api/billing", request)
    }

    def getBillingProjectStatus(projectName: String)(implicit token: AuthToken): Map[String, String] =
      parseResponseAs[Map[String, String]](getRequest(s"${url}api/user/billing/${projectName}"))

    def listMembersInBillingProject(projectName: String)(implicit token: AuthToken): List[Map[String, String]] = {
      logger.info(s"list members of billing project $projectName the caller owns")
      parseResponseAs[List[Map[String, String]]](getRequest(s"${url}api/billing/$projectName/members"))
    }

    def addUserToBillingProject(projectName: String, email: String, billingProjectRole: BillingProjectRole)(implicit
      token: AuthToken
    ): String = {
      logger.info(s"Adding user to billing project: $projectName $email ${billingProjectRole.toString}")
      putRequest(s"${url}api/billing/$projectName/${billingProjectRole.toString}/$email")
    }

    def removeUserFromBillingProject(projectName: String, email: String, billingProjectRole: BillingProjectRole)(
      implicit token: AuthToken
    ): String = {
      logger.info(s"Removing user from billing project: $projectName $email ${billingProjectRole.toString}")
      deleteRequest(s"${url}api/billing/$projectName/${billingProjectRole.toString}/$email")
    }

    def addGoogleRoleToBillingProjectUser(projectName: String, email: String, googleRole: String)(implicit
      token: AuthToken
    ): String = {
      logger.info(s"Adding google role $googleRole to user $email in billing project $projectName")
      putRequest(s"${url}api/billing/$projectName/googleRole/$googleRole/$email")
    }

    def removeGoogleRoleFromBillingProjectUser(projectName: String, email: String, googleRole: String)(implicit
      token: AuthToken
    ): String = {
      logger.info(s"Removing google role $googleRole from user $email in billing project $projectName")
      deleteRequest(s"${url}api/billing/$projectName/googleRole/$googleRole/$email")
    }

  }

  //noinspection RedundantBlock
  object billingV2 {

    def createBillingProject(projectName: String, billingAccount: String, servicePerimeterOpt: Option[String] = None)(
      implicit token: AuthToken
    ): String = {
      logger.info(s"Creating billing project $projectName in billing account $billingAccount")
      val request = Map("projectName" -> projectName, "billingAccount" -> billingAccount) ++ servicePerimeterOpt.map(
        servicePerimeter => "servicePerimeter" -> servicePerimeter
      )
      postRequest(s"${url}api/billing/v2", request)
    }

    def getBillingProject(projectName: String)(implicit token: AuthToken): Map[String, String] =
      parseResponseAs[Map[String, String]](getRequest(s"${url}api/billing/v2/${projectName}"))

    def deleteBillingProject(projectName: String)(implicit token: AuthToken): String =
      deleteRequest(s"${url}api/billing/v2/${projectName}")

    def listMembersInBillingProject(projectName: String)(implicit token: AuthToken): List[Map[String, String]] = {
      logger.info(s"list members of billing project $projectName the caller owns")
      parseResponseAs[List[Map[String, String]]](getRequest(s"${url}api/billing/v2/$projectName/members"))
    }

    def addUserToBillingProject(projectName: String, email: String, billingProjectRole: BillingProjectRole)(implicit
      token: AuthToken
    ): String = {
      logger.info(s"Adding user to billing project: $projectName $email ${billingProjectRole.toString}")
      putRequest(s"${url}api/billing/v2/$projectName/members/${billingProjectRole.toString}/$email")
    }

    def removeUserFromBillingProject(projectName: String, email: String, billingProjectRole: BillingProjectRole)(
      implicit token: AuthToken
    ): String = {
      logger.info(s"Removing user from billing project: $projectName $email ${billingProjectRole.toString}")
      deleteRequest(s"${url}api/billing/v2/$projectName/members/${billingProjectRole.toString}/$email")
    }
  }

  //noinspection RedundantBlock,ScalaUnnecessaryParentheses
  object methodConfigs {
    def copyMethodConfigFromWorkspace(
      sourceMethodConfig: Map[String, Any],
      destinationMethodConfigName: Map[String, Any]
    )(implicit token: AuthToken): String = {
      logger.info(s"Copying method configuration from workspace: ${sourceMethodConfig} ")

      val request = Map("source" -> sourceMethodConfig, "destination" -> destinationMethodConfigName)

      postRequest(url + "api/methodconfigs/copy", request)
    }

    def getMethodConfigInWorkspace(workspaceNamespace: String,
                                   workspaceName: String,
                                   configNamespace: String,
                                   configName: String
    )(implicit token: AuthToken): String = {
      logger.info(
        s"Getting method configuration $configNamespace/$configName for workspace ${workspaceNamespace}/${workspaceName}"
      )
      parseResponse(
        getRequest(
          url + s"api/workspaces/${workspaceNamespace}/${workspaceName}/methodconfigs/${configNamespace}/${configName}"
        )
      )
    }

    def copyMethodConfigFromMethodRepo(request: Map[String, Any])(implicit token: AuthToken): String = {
      logger.info(s"Copying method configuration from method repo: $request")
      postRequest((url + "api/methodconfigs/copyFromMethodRepo"), request)
    }

    def getMethodConfigSyntaxValidationInWorkspace(workspaceNamespace: String,
                                                   workspaceName: String,
                                                   configNamespace: String,
                                                   configName: String
    )(implicit token: AuthToken): String = {
      logger.info("Getting syntax validation for method configuration in workspace")
      parseResponse(
        getRequest(
          url + s"api/workspaces/${workspaceNamespace}/${workspaceName}/methodconfigs/${configNamespace}/${configName}/validate"
        )
      )
    }

    def createMethodConfigInWorkspace(workspaceNamespace: String,
                                      workspaceName: String,
                                      method: Method,
                                      configNamespace: String,
                                      configName: String,
                                      methodConfigVersion: Int,
                                      inputs: Map[String, String],
                                      outputs: Map[String, String],
                                      rootEntityType: String
    )(implicit token: AuthToken): String = {
      logger.info(
        s"Creating method config: $workspaceNamespace/$workspaceName $methodConfigVersion method: ${method.methodNamespace}/${method.methodName} config: $configNamespace/$configName"
      )
      postRequest(
        url + s"api/workspaces/$workspaceNamespace/$workspaceName/methodconfigs",
        Map(
          "deleted" -> false,
          "inputs" -> inputs,
          "methodConfigVersion" -> methodConfigVersion,
          "methodRepoMethod" -> method.methodRepoInfo,
          "namespace" -> configNamespace,
          "name" -> configName,
          "outputs" -> outputs,
          "prerequisites" -> Map.empty,
          "rootEntityType" -> rootEntityType
        )
      )
    }
  }

  object admin {
    def deleteBillingProject(projectName: String, projectOwner: UserInfo)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting billing project: $projectName")
      deleteRequestWithContent(
        url + s"api/admin/billing/$projectName",
        Map("newOwnerEmail" -> projectOwner.userEmail.value, "newOwnerToken" -> projectOwner.accessToken.token)
      )
    }

    def claimProject(projectName: String, cromwellAuthBucket: String, newOwner: UserInfo)(implicit
      token: AuthToken
    ): Unit = {
      logger.info(s"Claiming ownership of billing project: $projectName ${newOwner.userEmail}")
      postRequest(
        url + s"api/admin/project/registration",
        Map("project" -> projectName,
            "bucket" -> cromwellAuthBucket,
            "newOwnerEmail" -> newOwner.userEmail.value,
            "newOwnerToken" -> newOwner.accessToken.token
        )
      )
    }

    def releaseProject(projectName: String, projectOwner: UserInfo)(implicit token: AuthToken): Unit = {
      logger.info(s"Releasing ownership of billing project: $projectName")
      deleteRequestWithContent(
        url + s"api/admin/project/registration/$projectName",
        Map("newOwnerEmail" -> projectOwner.userEmail.value, "newOwnerToken" -> projectOwner.accessToken.token)
      )
    }

  }

  object entities {

    def importMetaData(namespace: String, workspaceName: String, upsertJson: Array[Map[String, Any]])(implicit
      token: AuthToken
    ): String = {
      logger.info(s"Importing metadata: $namespace/$workspaceName $upsertJson")
      postRequest(url + s"api/workspaces/$namespace/$workspaceName/entities/batchUpsert", upsertJson)
    }

  }

  object workspaces {

    def create(namespace: String, name: String, authDomain: Set[String] = Set.empty, bucketLocation: String = null)(implicit
      token: AuthToken
    ): Unit = {
      logger.info(s"Creating workspace: $namespace/$name authDomain: $authDomain")

      val authDomainGroups = authDomain.map(a => Map("membersGroupName" -> a))

      val request = Map("namespace" -> namespace,
                        "name" -> name,
                        "attributes" -> Map.empty,
                        "authorizationDomain" -> authDomainGroups,
                        "bucketLocation" -> bucketLocation
      )

      postRequest(url + s"api/workspaces", request)
    }

    def clone(sourceNamespace: String,
              sourceName: String,
              destNamespace: String,
              destName: String,
              authDomain: Set[String] = Set.empty,
              copyFilesWithPrefix: Option[String] = None
    )(implicit token: AuthToken): Unit = {
      logger.info(
        s"Cloning workspace: $sourceNamespace/$sourceName into $destNamespace/$destName authDomain: $authDomain, copyFilesWithPrefix: $copyFilesWithPrefix"
      )

      val authDomainGroups = authDomain.map(a => Map("membersGroupName" -> a))

      val request = Map(
        "namespace" -> destNamespace,
        "name" -> destName,
        "attributes" -> Map.empty,
        "authorizationDomain" -> authDomainGroups,
        "copyFilesWithPrefix" -> copyFilesWithPrefix
      )

      postRequest(url + s"api/workspaces/$sourceNamespace/$sourceName/clone", request)
    }

    def delete(namespace: String, name: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting workspace: $namespace/$name")
      deleteRequest(url + s"api/workspaces/$namespace/$name")
    }

    def getBucketName(namespace: String, name: String)(implicit token: AuthToken): String = {
      val response = parseResponse(getRequest(url + s"api/workspaces/$namespace/$name"))
      mapper.readTree(response).at("/workspace/bucketName").asText()
    }

    def getWorkflowCollectionName(namespace: String, name: String)(implicit token: AuthToken): String = {
      val response = parseResponse(getRequest(url + s"api/workspaces/$namespace/$name"))
      mapper.readTree(response).at("/workspace/workflowCollectionName").asText()
    }

    def list()(implicit token: AuthToken): String = {
      logger.info(s"Listing workspaces")
      parseResponse(getRequest(url + s"api/workspaces"))
    }

    def getWorkspaceDetails(namespace: String, name: String)(implicit token: AuthToken): String = {
      logger.info(s"Getting workspace $name in $namespace")
      parseResponse(getRequest(url + s"api/workspaces/$namespace/$name"))
    }

    def updateAcl(namespace: String, name: String, aclUpdates: Set[AclEntry], inviteUsersNotFound: Boolean = false)(
      implicit token: AuthToken
    ): String = {
      logger.info(s"Updating acl for workspace $name in $namespace")
      patchRequest(url + s"api/workspaces/$namespace/$name/acl?inviteUsersNotFound=$inviteUsersNotFound",
                   aclUpdates.map(e => e.toMap)
      )
    }

    def getAuthDomainsInWorkspace(namespace: String, name: String)(implicit token: AuthToken): List[String] = {
      import scala.collection.JavaConverters._
      val response = getWorkspaceDetails(namespace, name)
      mapper.readTree(response).at("/workspace/authorizationDomain").findValuesAsText("membersGroupName").asScala.toList
    }

    def getWorkspaceNames()(implicit token: AuthToken): List[String] = {
      import scala.collection.JavaConverters._
      val response = list()
      mapper.readTree(response).findValuesAsText("name").asScala.toList
    }

    def updateAttributes(namespace: String, name: String, attributeUpdates: List[AttributeUpdateOperation])(implicit
      token: AuthToken
    ): String = {
      logger.info(s"Setting attributes for workspace: $namespace/$name $attributeUpdates")

      // This puts the operations into a List[Map[String, String]] which gets parsed and sent along just how Rawls likes it
      val formattedOperations = attributeUpdates.map { attributeUpdate =>
        AttributeUpdateOperationFormat
          .write(attributeUpdate)
          .asJsObject
          .fields
          .mapValues(attrVal => attrVal.asInstanceOf[JsString].value)
      }
      patchRequest(url + s"api/workspaces/$namespace/$name", formattedOperations)
    }
  }

  object submissions {
    def launchWorkflow(billingProject: String,
                       workspaceName: String,
                       methodConfigurationNamespace: String,
                       methodConfigurationName: String,
                       entityType: String,
                       entityName: String,
                       expression: String,
                       useCallCache: Boolean,
                       deleteIntermediateOutputFiles: Boolean,
                       workflowFailureMode: String = "NoNewCalls"
    )(implicit token: AuthToken): String = {
      val body: Map[String, Any] = Map(
        "methodConfigurationNamespace" -> methodConfigurationNamespace,
        "methodConfigurationName" -> methodConfigurationName,
        "entityType" -> entityType,
        "entityName" -> entityName,
        "expression" -> expression,
        "useCallCache" -> useCallCache,
        "deleteIntermediateOutputFiles" -> deleteIntermediateOutputFiles,
        "workflowFailureMode" -> workflowFailureMode
      )
      logger.info(
        s"Creating a submission: $billingProject/$workspaceName config: $methodConfigurationNamespace/$methodConfigurationName with body $body"
      )
      val response = postRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions", body)

      // TODO properly parse SubmissionReport response (GAWB-3319)
      // Steps to do so:
      // 1. Move from Rawls Core to Rawls Model or Workbench Model
      // 2. Implement a Jackson de/serializer for SubmissionReport and all dependent case classes

      response.fromJsonMapAs[String]("submissionId") getOrElse (throw RestException(
        s"Can't parse submissionId from SubmissionReport response $response"
      ))
    }

    // returns a tuple of (submission status, workflow IDs if any)
    def getSubmissionStatus(billingProject: String, workspaceName: String, submissionId: String)(implicit
      token: AuthToken
    ): (String, List[String]) = {
      logger.info(s"Get submission status: $billingProject/$workspaceName/$submissionId")
      val response = parseResponse(
        getRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId")
      )

      // TODO properly parse SubmissionStatusResponse (GAWB-3319)
      // Steps to do so:
      // 1. Move from Rawls Core to Rawls Model or Workbench Model
      // 2. Implement a Jackson de/serializer for SubmissionStatusResponse and all dependent case classes

      val status = response.fromJsonMapAs[String]("status") getOrElse (throw RestException(
        s"Can't parse status from SubmissionStatusResponse response $response"
      ))

      // workflows are JSON maps with (optional) workflowIds.  Collect the IDs that are defined

      import scala.collection.JavaConverters._
      val workflows: List[JsonNode] = mapper.readTree(response).get("workflows").elements().asScala.toList

      val ids = workflows flatMap { wf =>
        Try(wf.get("workflowId").textValue()).toOption
      }

      (status, ids)
    }

    def getWorkflowMetadata(billingProject: String, workspaceName: String, submissionId: String, workflowId: String)(
      implicit token: AuthToken
    ): String = {
      logger.info(s"Get workflow metadata: $billingProject/$workspaceName/$submissionId/$workflowId")
      parseResponse(
        getRequest(
          url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId/workflows/$workflowId"
        )
      )
    }

    def getWorkflowOutputs(billingProject: String, workspaceName: String, submissionId: String, workflowId: String)(
      implicit token: AuthToken
    ): String = {
      logger.info(s"Get workflow outputs: $billingProject/$workspaceName/$submissionId/$workflowId")
      parseResponse(
        getRequest(
          url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId/workflows/$workflowId/outputs"
        )
      )
    }

    def abortSubmission(billingProject: String, workspaceName: String, submissionId: String)(implicit
      token: AuthToken
    ): String = {
      logger.info(s"Abort submission: $billingProject/$workspaceName/$submissionId")
      deleteRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId")
    }
  }

}

object Rawls extends Rawls
