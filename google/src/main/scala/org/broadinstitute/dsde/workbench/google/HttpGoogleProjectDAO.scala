package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.http.HttpResponseException
import com.google.api.services.cloudresourcemanager.CloudResourceManager
import com.google.api.services.cloudresourcemanager.model.{Operation, Project}
import com.google.api.services.compute.ComputeScopes
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes.GoogleCredentialMode
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService

import scala.concurrent.{ExecutionContext, Future}

class HttpGoogleProjectDAO(appName: String,
                           googleCredentialMode: GoogleCredentialMode,
                           workbenchMetricBaseName: String)
                          (implicit system: ActorSystem, executionContext: ExecutionContext)
  extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName) with GoogleProjectDAO {

  override val scopes = Seq(ComputeScopes.CLOUD_PLATFORM)

  override implicit val service = GoogleInstrumentedService.Projects

  private lazy val cloudResManager = {
    new CloudResourceManager.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  override def createProject(projectName: String): Future[String] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(cloudResManager.projects().create(new Project().setName(projectName).setProjectId(projectName)))
    }).map { operation =>
      operation.getName
    }
  }

  override def pollOperation(operationId: String): Future[Operation] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(cloudResManager.operations().get(operationId))
    })
  }

  def isProjectActive(projectName: String): Future[Boolean] = {
    retryWithRecoverWhen500orGoogleError { () =>
      // get the project
      Option(executeGoogleRequest(cloudResManager.projects().get(projectName)))
    } {
      // if the project doesn't exist, don't fail
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
    } map {
      // return true if the project is active, false otherwise
      // see https://cloud.google.com/resource-manager/reference/rest/v1/projects#LifecycleState
      case Some(project) => project.getLifecycleState == "ACTIVE"
      case None => false
    }
  }

}
