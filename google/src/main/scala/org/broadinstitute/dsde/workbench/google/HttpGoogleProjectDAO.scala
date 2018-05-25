package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import com.google.api.services.cloudresourcemanager.CloudResourceManager
import com.google.api.services.cloudresourcemanager.model.Project
import com.google.api.services.compute.ComputeScopes
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes.GoogleCredentialMode
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model.WorkbenchException

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

  override def pollOperation(operationId: String): Future[Boolean] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(cloudResManager.operations().get(operationId))
    }).map { operation =>
      if(operation.getDone) {
        Option(operation.getError) match {
          case Some(error) => throw new WorkbenchException(s"encountered error $error during operation $operationId")
          case None => true
        }
      }
      else false
    }
  }

}
