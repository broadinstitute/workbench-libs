package org.broadinstitute.dsde.workbench.google

import com.google.api.services.servicemanagement.model.Operation

import scala.concurrent.Future

trait GoogleServiceManagerDAO {

  def enableService(projectName: String, serviceName: String): Future[String]

  def pollOperation(operationId: String): Future[Operation]

}