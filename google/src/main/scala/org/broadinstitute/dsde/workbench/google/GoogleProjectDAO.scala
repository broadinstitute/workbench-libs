package org.broadinstitute.dsde.workbench.google

import com.google.api.services.cloudresourcemanager.model.Operation

import scala.concurrent.Future

trait GoogleProjectDAO {

  def createProject(projectName: String): Future[String]

  def pollOperation(operationId: String): Future[Operation]

}
