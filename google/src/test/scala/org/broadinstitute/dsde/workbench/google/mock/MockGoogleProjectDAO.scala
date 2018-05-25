package org.broadinstitute.dsde.workbench.google.mock

import java.util.UUID

import org.broadinstitute.dsde.workbench.google.GoogleProjectDAO

import scala.concurrent.Future

class MockGoogleProjectDAO extends GoogleProjectDAO {
  override def createProject(projectName: String): Future[String] = Future.successful(UUID.randomUUID().toString)

  override def pollOperation(operationId: String): Future[Boolean] = Future.successful(true)
}
