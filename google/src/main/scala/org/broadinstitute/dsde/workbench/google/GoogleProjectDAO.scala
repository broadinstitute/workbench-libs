package org.broadinstitute.dsde.workbench.google

import scala.concurrent.Future

trait GoogleProjectDAO {

  def createProject(projectName: String): Future[String]

  def pollOperation(operationId: String): Future[Boolean]

}
