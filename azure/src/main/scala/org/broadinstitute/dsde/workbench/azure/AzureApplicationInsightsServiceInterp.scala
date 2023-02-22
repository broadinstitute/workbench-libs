package org.broadinstitute.dsde.workbench.azure

import cats.effect.Async
import cats.mtl.Ask
import cats.syntax.all._
import com.azure.core.management.AzureEnvironment
import com.azure.core.management.profile.AzureProfile
import com.azure.identity.ClientSecretCredential
import com.azure.resourcemanager.applicationinsights.ApplicationInsightsManager
import com.azure.resourcemanager.applicationinsights.models.ApplicationInsightsComponent
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.tracedLogging
import org.typelevel.log4cats.StructuredLogger

class AzureApplicationInsightsServiceInterp[F[_]](clientSecretCredential: ClientSecretCredential)(implicit
  val F: Async[F],
  logger: StructuredLogger[F]
) extends AzureApplicationInsightsService[F] {

  override def getApplicationInsights(name: ApplicationInsightsName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[ApplicationInsightsComponent] =
    for {
      mgr <- buildApplicationInsightsManager(cloudContext)
      resp <- tracedLogging(
        F.delay(
          mgr.components().getByResourceGroup(cloudContext.managedResourceGroupName.value, name.value)
        ),
        s"com.azure.resourcemanager.applicationinsights,listByResourceGroup(${cloudContext.managedResourceGroupName.value})"
      )
    } yield resp

  private def buildApplicationInsightsManager(cloudContext: AzureCloudContext): F[ApplicationInsightsManager] = {
    val azureProfile =
      new AzureProfile(cloudContext.tenantId.value, cloudContext.subscriptionId.value, AzureEnvironment.AZURE)
    F.delay(ApplicationInsightsManager.authenticate(clientSecretCredential, azureProfile))
  }
}
