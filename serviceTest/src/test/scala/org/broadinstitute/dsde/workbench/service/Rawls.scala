package org.broadinstitute.dsde.workbench.service

import com.fasterxml.jackson.databind.JsonNode
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import org.broadinstitute.dsde.workbench.model.UserInfo

import scala.util.Try

trait Rawls extends RestClient with LazyLogging {

  val url: String = ServiceTestConfig.FireCloud.rawlsApiUrl

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

    def delete(namespace: String, name: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting workspace: $namespace/$name")
      deleteRequest(url + s"api/workspaces/$namespace/$name")
    }

    def list()(implicit token: AuthToken):String  = {
      logger.info(s"Listing workspaces")
      parseResponse(getRequest(url + s"api/workspaces"))
    }
  }

  object submissions {
    def launchWorkflow(billingProject: String, workspaceName: String, methodConfigurationNamespace: String, methodConfigurationName: String, entityType: String, entityName: String, expression: String, useCallCache: Boolean, workflowFailureMode: String = "NoNewCalls")(implicit token: AuthToken): String = {
      val body = Map("methodConfigurationNamespace" -> methodConfigurationNamespace, "methodConfigurationName" -> methodConfigurationName, "entityType" -> entityType, "entityName" -> entityName, "expression" -> expression, "useCallCache" -> useCallCache, "workflowFailureMode" -> workflowFailureMode)
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
      logger.info(s"Get workflow metadata: $billingProject/$workspaceName/$submissionId/$workflowId")
      parseResponse(getRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId/workflows/$workflowId/outputs"))
    }

    def abortSubmission(billingProject: String, workspaceName: String, submissionId: String)(implicit token: AuthToken): String = {
      logger.info(s"Abort submission: $billingProject/$workspaceName/$submissionId")
      deleteRequest(url + s"api/workspaces/$billingProject/$workspaceName/submissions/$submissionId")
    }
  }

}

object Rawls extends Rawls
