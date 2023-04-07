package org.broadinstitute.dsde.workbench.azure

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.resourcemanager.applicationinsights.models.ApplicationInsightsComponent
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger

trait AzureApplicationInsightsService[F[_]] {

  def getApplicationInsights(name: ApplicationInsightsName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[ApplicationInsightsComponent]

}

object AzureApplicationInsightsService {
  def fromAzureAppRegistrationConfig[F[_]: Async: StructuredLogger](
    azureAppRegistrationConfig: AzureAppRegistrationConfig
  ): Resource[F, AzureApplicationInsightsService[F]] = {
    val clientSecretCredential = new ClientSecretCredentialBuilder()
      .clientId(azureAppRegistrationConfig.clientId.value)
      .clientSecret(azureAppRegistrationConfig.clientSecret.value)
      .tenantId(azureAppRegistrationConfig.managedAppTenantId.value)
      .build
    Resource.eval(Async[F].pure(new AzureApplicationInsightsServiceInterp(clientSecretCredential)))
  }
}
