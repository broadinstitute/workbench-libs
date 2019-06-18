package org.broadinstitute.dsde.workbench.google

import com.google.api.services.cloudresourcemanager.model.{Ancestor, Operation}
import org.broadinstitute.dsde.workbench.model.google.GoogleResourceTypes.GoogleParentResourceType

import scala.concurrent.Future

trait GoogleProjectDAO {

  def createProject(projectName: String): Future[String]

  def createProject(projectName: String, parentId: String, parentType: GoogleParentResourceType): Future[String]

  def pollOperation(operationId: String): Future[Operation]

  def isProjectActive(projectName: String): Future[Boolean]

  def isBillingActive(projectName: String): Future[Boolean]

  def enableService(projectName: String, serviceName: String): Future[String]

  def getLabels(projectName: String): Future[Map[String, String]]

  def getAncestry(projectName: String): Future[Seq[Ancestor]]
}
