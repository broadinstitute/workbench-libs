package org.broadinstitute.dsde.workbench.google.mock

import java.util.UUID

import com.google.api.services.cloudresourcemanager.model.{Ancestor, Operation, ResourceId}
import org.broadinstitute.dsde.workbench.google.GoogleProjectDAO
import org.broadinstitute.dsde.workbench.model.google.GoogleResourceTypes.GoogleParentResourceType

import scala.concurrent.Future

class MockGoogleProjectDAO extends GoogleProjectDAO {
  override def createProject(projectName: String): Future[String] = Future.successful(UUID.randomUUID().toString)

  override def createProject(projectName: String,
                             parentId: String,
                             parentType: GoogleParentResourceType
  ): Future[String] =
    Future.successful(UUID.randomUUID().toString)

  override def pollOperation(operationId: String): Future[Operation] = Future.successful(new Operation)

  override def isProjectActive(projectName: String): Future[Boolean] = Future.successful(true)

  override def isBillingActive(projectName: String): Future[Boolean] = Future.successful(true)

  override def enableService(projectName: String, serviceName: String): Future[String] =
    Future.successful(UUID.randomUUID().toString)

  override def getLabels(projectName: String): Future[Map[String, String]] = Future.successful(Map.empty)

  override def getAncestry(projectName: String): Future[Seq[Ancestor]] =
    Future.successful(
      Seq(new Ancestor.setResourceId(new ResourceId.setId("mock-org-number").setType("organization")))
    )

  override def getProjectNumber(projectName: String): Future[Option[Long]] = Future.successful(Some(1234))

  override def getProjectName(projectId: String): Future[Option[String]] = Future.successful(Some("mock-project-name"))
}
