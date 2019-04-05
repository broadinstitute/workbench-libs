package org.broadinstitute.dsde.workbench.service

import com.fasterxml.jackson.databind.JsonNode
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import org.broadinstitute.dsde.workbench.model.UserInfo
import org.broadinstitute.dsde.workbench.service.BillingProject.BillingProjectRole.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.BillingProject.{BillingProjectRole, BillingProjectStatus}
import org.broadinstitute.dsde.workbench.service.util.Retry

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

trait Rawls extends RestClient with LazyLogging {

  val url: String = ServiceTestConfig.FireCloud.rawlsApiUrl

  def responseAsList[T](response: String): List[Map[String, T]] = {
    mapper.readValue(response, classOf[List[Map[String, T]]])
  }

  object billing {

    def createBillingProject(projectName: String, billingAccount: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Creating billing project: $projectName in billing account $billingAccount")

      postRequest(url +"api/billing", Map("projectName" -> projectName, "billingAccount" -> billingAccount))

      // wait for done
      waitUntilBillingProjectIsReady(projectName, billingAccount)
    }

    def listMembersInBillingProject(projectName: String)(implicit token: AuthToken): List[Map[String, String]] = {
      logger.info(s"list members of billing project $projectName the caller owns")
      parseResponseAs[List[Map[String, String]]](getRequest(s"${url}api/billing/$projectName/members"))
    }

    def addUserToBillingProject(projectName: String, email: String, billingProjectRole: BillingProjectRole)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding user to billing project: $projectName $email ${billingProjectRole.toString}")
      putRequest(s"${url}api/billing/$projectName/${billingProjectRole.toString}/$email")
    }

    def removeUserFromBillingProject(projectName: String, email: String, billingProjectRole: BillingProjectRole)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing user from billing project: $projectName $email ${billingProjectRole.toString}")
      deleteRequest(s"${url}api/billing/$projectName/${billingProjectRole.toString}/$email")
    }

    def addGoogleRoleToBillingProjectUser(projectName: String, email: String, googleRole: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding google role $googleRole to user $email in billing project $projectName")
      putRequest(s"${url}api/billing/$projectName/googleRole/$googleRole/$email")
    }

    def removeGoogleRoleFromBillingProjectUser(projectName: String, email: String, googleRole: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing google role $googleRole from user $email in billing project $projectName")
      deleteRequest(s"${url}api/billing/$projectName/googleRole/$googleRole/$email")
    }

    private def waitUntilBillingProjectIsReady(projectName: String, billingAccount: String)(implicit token: AuthToken): Unit = {
      // wait for done
      Retry.retry(30.seconds, 20.minutes)({
        Try(responseAsList[String](parseResponse(getRequest(s"${ServiceTestConfig.FireCloud.orchApiUrl}api/profile/billing")))) match {
          case Success(response) => response.map { p =>
            BillingProject(p("projectName"), BillingProjectRole.withName(p("role")), BillingProjectStatus.withName(p("creationStatus")))
          }.find(p => p.projectName == projectName && BillingProjectStatus.isTerminal(p.creationStatus))
          case Failure(t) => logger.error(s"Billing project creation encountered an error: ${t.getStackTrace}");
            None
        }
      }) match {
        case Some(BillingProject(name, _, BillingProjectStatus.Ready)) =>
          logger.info(s"Finished creating billing project: $name in billing account $billingAccount")
        case Some(BillingProject(name, _, _)) =>
          logger.info(s"Encountered an error creating billing project: $name in billing account $billingAccount")
          throw new Exception("Billing project creation encountered an error")
        case None => throw new Exception("Billing project creation did not complete successfully")
      }
    }
  }

  object admin {
    def deleteBillingProject(projectName: String, projectOwner: UserInfo)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting billing project: $projectName")
      deleteRequestWithContent(url + s"api/admin/billing/$projectName", Map("newOwnerEmail" -> projectOwner.userEmail.value, "newOwnerToken" -> projectOwner.accessToken.token))
    }

    def claimProject(projectName: String, cromwellAuthBucket: String, newOwner: UserInfo)(implicit token: AuthToken): Unit = {
      logger.info(s"Claiming ownership of billing project: $projectName ${newOwner.userEmail}")
      postRequest(url + s"api/admin/project/registration", Map("project" -> projectName, "bucket" -> cromwellAuthBucket, "newOwnerEmail" -> newOwner.userEmail.value, "newOwnerToken" -> newOwner.accessToken.token))
    }

    def releaseProject(projectName: String, projectOwner: UserInfo)(implicit token: AuthToken): Unit = {
      logger.info(s"Releasing ownership of billing project: $projectName")
      deleteRequestWithContent(url + s"api/admin/project/registration/$projectName", Map("newOwnerEmail" -> projectOwner.userEmail.value, "newOwnerToken" -> projectOwner.accessToken.token))
    }

  }

  object workspaces {

    def create(namespace: String, name: String, authDomain: Set[String] = Set.empty)
              (implicit token: AuthToken): Unit = {
      logger.info(s"Creating workspace: $namespace/$name authDomain: $authDomain")

      val authDomainGroups = authDomain.map(a => Map("membersGroupName" -> a))

      val request = Map("namespace" -> namespace, "name" -> name,
        "attributes" -> Map.empty, "authorizationDomain" -> authDomainGroups)

      postRequest(url + s"api/workspaces", request)
    }

    def clone(sourceNamespace: String, sourceName: String, destNamespace: String, destName: String, authDomain: Set[String] = Set.empty, copyFilesWithPrefix: Option[String] = None)
             (implicit token: AuthToken): Unit = {
      logger.info(s"Cloning workspace: $sourceNamespace/$sourceName into $destNamespace/$destName authDomain: $authDomain, copyFilesWithPrefix: $copyFilesWithPrefix")

      val authDomainGroups = authDomain.map(a => Map("membersGroupName" -> a))

      val request = Map("namespace" -> destNamespace, "name" -> destName,
        "attributes" -> Map.empty, "authorizationDomain" -> authDomainGroups, "copyFilesWithPrefix" -> copyFilesWithPrefix)

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

    def list()(implicit token: AuthToken):String  = {
      logger.info(s"Listing workspaces")
      parseResponse(getRequest(url + s"api/workspaces"))
    }

    def getWorkspaceDetails(namespace: String, name: String)(implicit token: AuthToken): String = {
      logger.info(s"Getting workspace $name in $namespace")
      parseResponse(getRequest(url + s"api/workspaces/$namespace/$name"))
    }
  }

  object submissions {
    def launchWorkflow(billingProject: String, workspaceName: String, methodConfigurationNamespace: String, methodConfigurationName: String, entityType: String, entityName: String, expression: String, useCallCache: Boolean, workflowFailureMode: String = "NoNewCalls")(implicit token: AuthToken): String = {
      val body: Map[String, Any] = Map("methodConfigurationNamespace" -> methodConfigurationNamespace, "methodConfigurationName" -> methodConfigurationName, "entityType" -> entityType, "entityName" -> entityName, "expression" -> expression, "useCallCache" -> useCallCache, "workflowFailureMode" -> workflowFailureMode)
      logger.info(s"Creating a submission: $billingProject/$workspaceName config: $methodConfigurationNamespace/$methodConfigurationName with body $body")
      val response = postRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions", body)

      // TODO properly parse SubmissionReport response (GAWB-3319)
      // Steps to do so:
      // 1. Move from Rawls Core to Rawls Model or Workbench Model
      // 2. Implement a Jackson de/serializer for SubmissionReport and all dependent case classes

      response.fromJsonMapAs[String]("submissionId") getOrElse (throw RestException(s"Can't parse submissionId from SubmissionReport response $response"))
    }

    // returns a tuple of (submission status, workflow IDs if any)
    def getSubmissionStatus(billingProject: String, workspaceName: String, submissionId: String)(implicit token: AuthToken): (String, List[String]) = {
      logger.info(s"Get submission status: $billingProject/$workspaceName/$submissionId")
      val response = parseResponse(getRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId"))

      // TODO properly parse SubmissionStatusResponse (GAWB-3319)
      // Steps to do so:
      // 1. Move from Rawls Core to Rawls Model or Workbench Model
      // 2. Implement a Jackson de/serializer for SubmissionStatusResponse and all dependent case classes

      val status = response.fromJsonMapAs[String]("status") getOrElse (throw RestException(s"Can't parse status from SubmissionStatusResponse response $response"))

      // workflows are JSON maps with (optional) workflowIds.  Collect the IDs that are defined

      import scala.collection.JavaConverters._
      val workflows: List[JsonNode] = mapper.readTree(response).get("workflows").elements().asScala.toList

      val ids = workflows flatMap { wf =>
        Try(wf.get("workflowId").textValue()).toOption
      }

      (status, ids)
    }

    def getWorkflowMetadata(billingProject: String, workspaceName: String, submissionId: String, workflowId: String)(implicit token: AuthToken): String = {
      logger.info(s"Get workflow metadata: $billingProject/$workspaceName/$submissionId/$workflowId")
      parseResponse(getRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId/workflows/$workflowId"))
    }

    def getWorkflowOutputs(billingProject: String, workspaceName: String, submissionId: String, workflowId: String)(implicit token: AuthToken): String = {
      logger.info(s"Get workflow outputs: $billingProject/$workspaceName/$submissionId/$workflowId")
      parseResponse(getRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId/workflows/$workflowId/outputs"))
    }

    def abortSubmission(billingProject: String, workspaceName: String, submissionId: String)(implicit token: AuthToken): String = {
      logger.info(s"Abort submission: $billingProject/$workspaceName/$submissionId")
      deleteRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId")
    }
  }

}

object Rawls extends Rawls
