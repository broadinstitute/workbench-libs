package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.http.HttpResponseException
import com.google.api.services.cloudbilling.Cloudbilling
import com.google.api.services.cloudresourcemanager.CloudResourceManager
import com.google.api.services.cloudresourcemanager.model._
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.servicemanagement.ServiceManagement
import com.google.api.services.servicemanagement.model.EnableServiceRequest
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes.GoogleCredentialMode
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model.google.GoogleResourceTypes.GoogleParentResourceType

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class HttpGoogleProjectDAO(appName: String,
                           googleCredentialMode: GoogleCredentialMode,
                           workbenchMetricBaseName: String)
                          (implicit system: ActorSystem, executionContext: ExecutionContext)
  extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName) with GoogleProjectDAO {

  override val scopes = Seq(ComputeScopes.CLOUD_PLATFORM)

  override implicit val service = GoogleInstrumentedService.Projects

  private def cloudResManager = {
    new CloudResourceManager.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  private def serviceManagement = {
    new ServiceManagement.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  private def billing: Cloudbilling = {
    new Cloudbilling.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  override def createProject(projectName: String): Future[String] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(cloudResManager.projects().create(new Project().setName(projectName).setProjectId(projectName)))
    }).map { operation =>
      operation.getName
    }
  }

  override def createProject(projectName: String, parentId: String, parentType: GoogleParentResourceType): Future[String] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(cloudResManager.projects().create(new Project().setName(projectName).setProjectId(projectName)
        .setParent(new ResourceId().setId(parentId).setType(parentType.value))))
    }).map { operation =>
      operation.getName
    }
  }

  override def pollOperation(operationId: String): Future[Operation] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(cloudResManager.operations().get(operationId))
    })
  }

  override def isProjectActive(projectName: String): Future[Boolean] = {
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

  override def isBillingActive(projectName: String): Future[Boolean] = {
    retryWithRecoverWhen500orGoogleError { () =>
      Option(executeGoogleRequest(billing.projects().getBillingInfo(s"projects/$projectName")))
    } {
      // if the project doesn't exist, don't fail
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
    } map {
      // return true if billing is enabled for the project, false otherwise
      case Some(billingInfo) => billingInfo.getBillingEnabled
      case None => false
    }
  }

  override def enableService(projectName: String, serviceName: String): Future[String] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(serviceManagement.services().enable(serviceName, new EnableServiceRequest().setConsumerId(s"project:$projectName")))
    }).map { operation =>
      operation.getName
    }
  }

  override def getLabels(projectName: String): Future[Map[String, String]] = {
    import scala.collection.JavaConverters._
    retryWhen500orGoogleError { () =>
      // get the project
      executeGoogleRequest(cloudResManager.projects().get(projectName))
    } map { project =>
      project.getLabels.asScala.toMap
    }
  }

  override def getAncestry(projectName: String): Future[Seq[Ancestor]] = {
    retryWhen500orGoogleError(() => {
      executeGoogleRequest(cloudResManager.projects().getAncestry(projectName, new GetAncestryRequest()))
    }).map { ancestry =>
      Option(ancestry.getAncestor).map(_.asScala).getOrElse(Seq.empty)
    }
  }
}
