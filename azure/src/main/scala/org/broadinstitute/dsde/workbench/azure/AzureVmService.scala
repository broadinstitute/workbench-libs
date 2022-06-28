package org.broadinstitute.dsde.workbench.azure

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.resourcemanager.compute.models.VirtualMachine
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger

trait AzureVmService[F[_]] {
  def getAzureVm(name: String, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[VirtualMachine]]

  def deleteAzureVm(name: String, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[VirtualMachine]]
}

object AzureVmService {
  def fromAzureAppRegistrationConfig[F[_]: Async: StructuredLogger](
    azureAppRegistrationConfig: AzureAppRegistrationConfig
  ): Resource[F, AzureVmService[F]] = {
    val clientSecretCredential = new ClientSecretCredentialBuilder()
      .clientId(azureAppRegistrationConfig.clientId.value)
      .clientSecret(azureAppRegistrationConfig.clientSecret.value)
      .tenantId(azureAppRegistrationConfig.managedAppTenantId.value)
      .build
    Resource.eval(Async[F].pure(new AzureVmServiceInterp(clientSecretCredential)))
  }
}
