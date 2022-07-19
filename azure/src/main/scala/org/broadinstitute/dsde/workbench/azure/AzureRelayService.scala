package org.broadinstitute.dsde.workbench.azure

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.azure.identity.ClientSecretCredentialBuilder
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger

trait AzureRelayService[F[_]] {
  def createRelayHybridConnection(relayNamespace: RelayNamespace,
                                  hybridConnectionName: RelayHybridConnectionName,
                                  cloudContext: AzureCloudContext
  )(implicit
    ev: Ask[F, TraceId]
  ): F[PrimaryKey]

  def deleteRelayHybridConnection(relayNamespace: RelayNamespace,
                                  hybridConnectionName: RelayHybridConnectionName,
                                  cloudContext: AzureCloudContext
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]
}

object AzureRelayService {
  def fromAzureAppRegistrationConfig[F[_]: Async: StructuredLogger](
    azureAppRegistrationConfig: AzureAppRegistrationConfig
  ): Resource[F, AzureRelayService[F]] = {
    val clientSecretCredential = new ClientSecretCredentialBuilder()
      .clientId(azureAppRegistrationConfig.clientId.value)
      .clientSecret(azureAppRegistrationConfig.clientSecret.value)
      .tenantId(azureAppRegistrationConfig.managedAppTenantId.value)
      .build
    Resource.eval(Async[F].pure(new AzureRelayInterp(clientSecretCredential)))
  }
}
