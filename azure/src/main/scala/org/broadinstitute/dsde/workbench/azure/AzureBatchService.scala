package org.broadinstitute.dsde.workbench.azure

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.resourcemanager.batch.models.BatchAccount
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger

trait AzureBatchService[F[_]] {

  def getBatchAccount(name: BatchAccountName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[BatchAccount]

}

object AzureBatchService {
  def fromAzureAppRegistrationConfig[F[_]: Async: StructuredLogger](
    azureAppRegistrationConfig: AzureAppRegistrationConfig
  ): Resource[F, AzureBatchService[F]] = {
    val clientSecretCredential = new ClientSecretCredentialBuilder()
      .clientId(azureAppRegistrationConfig.clientId.value)
      .clientSecret(azureAppRegistrationConfig.clientSecret.value)
      .tenantId(azureAppRegistrationConfig.managedAppTenantId.value)
      .build
    Resource.eval(Async[F].pure(new AzureBatchServiceInterp(clientSecretCredential)))
  }
}

