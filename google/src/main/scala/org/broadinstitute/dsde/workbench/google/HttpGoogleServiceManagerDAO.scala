package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.servicemanagement.ServiceManagement
import com.google.api.services.servicemanagement.model.Operation
import com.google.api.services.servicemanagement.model.EnableServiceRequest
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes.GoogleCredentialMode
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService

import scala.concurrent.{ExecutionContext, Future}

class HttpGoogleServiceManagerDAO(appName: String,
                           googleCredentialMode: GoogleCredentialMode,
                           workbenchMetricBaseName: String)
                          (implicit system: ActorSystem, executionContext: ExecutionContext)
  extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName) with GoogleServiceManagerDAO {

  override implicit val service = GoogleInstrumentedService.ServiceManager

  override val scopes = Seq(ComputeScopes.CLOUD_PLATFORM)

  private lazy val servicesManager = {
    new ServiceManagement.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  override def enableService(projectName: String, serviceName: String): Future[String] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(servicesManager.services().enable(serviceName, new EnableServiceRequest().setConsumerId(s"project:$projectName")))
    }).map { operation =>
      operation.getName
    }
  }

  override def pollOperation(operationId: String): Future[Operation] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(servicesManager.operations().get(operationId))
    })
  }

}
