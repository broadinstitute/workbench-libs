package org.broadinstitute.dsde.workbench.azure

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.resourcemanager.compute.models.VirtualMachine
import com.azure.resourcemanager.resources.fluentcore.model.Accepted
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.InstanceName
import org.typelevel.log4cats.StructuredLogger
import reactor.core.publisher.Mono

trait AzureVmService[F[_]] {
  def getAzureVm(name: InstanceName, cloudContext: AzureCloudContext)(implicit
                                                                      ev: Ask[F, TraceId]
  ): F[Option[VirtualMachine]]

  def deleteAzureVm(name: InstanceName, cloudContext: AzureCloudContext, forceDeletion: Boolean)(implicit
                                                                                                 ev: Ask[F, TraceId]
  ): F[Option[Accepted[Void]]]

  def startAzureVm(name: InstanceName, cloudContext: AzureCloudContext)(implicit
                                                                        ev: Ask[F, TraceId]
  ): F[Option[Mono[Void]]]

  def stopAzureVm(name: InstanceName, cloudContext: AzureCloudContext)(implicit
                                                                       ev: Ask[F, TraceId]
  ): F[Option[Mono[Void]]]
}

object AzureVmService {
  def fromAzureAppRegistrationConfig[F[_] : Async : StructuredLogger](
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
