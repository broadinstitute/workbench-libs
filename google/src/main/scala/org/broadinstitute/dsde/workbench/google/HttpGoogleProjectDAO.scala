package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import com.google.api.services.cloudresourcemanager.CloudResourceManager
import com.google.api.services.cloudresourcemanager.model.{Operation, Project}
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.servicemanagement.ServiceManagement
import com.google.api.services.servicemanagement.model.EnableServiceRequest
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

  private lazy val servicesManager = {
    new ServiceManagement.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  override def createProject(projectName: String): Future[String] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(cloudResManager.projects().create(new Project().setName(projectName).setProjectId(projectName)))
    }).map { operation =>
      operation.getName
    }
  }

  override def pollOperation(operationId: String): Future[com.google.api.services.cloudresourcemanager.model.Operation] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(cloudResManager.operations().get(operationId))
    })
  }

  override def enableService(projectName: String, service: String): Future[String] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(servicesManager.services().enable(service, new EnableServiceRequest().setConsumerId(s"project:$projectName")))
    }).map { operation =>
      operation.getName
    }
  }

  override def pollServiceOperation(operationId: String): Future[com.google.api.services.servicemanagement.model.Operation] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(servicesManager.operations().get(operationId))
    })
  }

}
