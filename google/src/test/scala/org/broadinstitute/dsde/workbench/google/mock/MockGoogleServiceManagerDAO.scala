package org.broadinstitute.dsde.workbench.google.mock

import java.util.UUID

import org.broadinstitute.dsde.workbench.google.GoogleServiceManagerDAO

import com.google.api.services.servicemanagement.model.Operation

import scala.concurrent.Future

class MockGoogleServiceManagerDAO extends GoogleServiceManagerDAO {

  override def enableService(projectName: String, serviceName: String): Future[String] = Future.successful(UUID.randomUUID().toString)

  override def pollOperation(operationId: String): Future[Operation] = Future.successful(new Operation)

}
