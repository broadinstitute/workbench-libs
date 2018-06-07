package org.broadinstitute.dsde.workbench.google.mock

import java.util.UUID

import com.google.api.services.cloudresourcemanager.model.Operation
import org.broadinstitute.dsde.workbench.google.GoogleProjectDAO

import scala.concurrent.Future

class MockGoogleProjectDAO extends GoogleProjectDAO {
  override def createProject(projectName: String): Future[String] = Future.successful(UUID.randomUUID().toString)

  override def pollOperation(operationId: String): Future[com.google.api.services.cloudresourcemanager.model.Operation] = Future.successful(new com.google.api.services.cloudresourcemanager.model.Operation)

}
