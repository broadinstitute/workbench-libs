package org.broadinstitute.dsde.workbench.azure

import cats.effect.Async
import cats.implicits._
import cats.mtl.Ask
import com.azure.core.management.AzureEnvironment
import com.azure.core.management.exception.ManagementException
import com.azure.core.management.profile.AzureProfile
import com.azure.identity.ClientSecretCredential
import com.azure.resourcemanager.compute.ComputeManager
import com.azure.resourcemanager.compute.models.VirtualMachine
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.tracedLogging
import org.typelevel.log4cats.StructuredLogger

class AzureVmServiceInterp[F[_]](clientSecretCredential: ClientSecretCredential)(implicit
  val F: Async[F],
  logger: StructuredLogger[F]
) extends AzureVmService[F] {

  def getAzureVm(name: String, cloudContext: AzureCloudContext)(implicit
    logger: StructuredLogger[F],
    ev: Ask[F, TraceId]
  ): F[Option[VirtualMachine]] =
    for {
      azureComputeManager <- buildComputeManager(cloudContext)

      fa = F
        .delay(
          azureComputeManager
            .virtualMachines()
            .getByResourceGroup(cloudContext.managedResourceGroupName.value, name)
        )
        .map(Option(_))
        .handleErrorWith {
          case e: ManagementException if e.getValue.getCode().equals("ResourceNotFound") => F.pure(none[VirtualMachine])
          case e => F.raiseError[Option[VirtualMachine]](e)
        }
      res <- tracedLogging(
        fa,
        s"com.azure.resourcemanager.resources.fluentcore.arm.collection.SupportsGettingByResourceGroup.getByResourceGroup(${cloudContext.managedResourceGroupName.value}, ${name})"
      )
    } yield res

  private def buildComputeManager(azureCloudContext: AzureCloudContext): F[ComputeManager] = {
    val azureProfile =
      new AzureProfile(azureCloudContext.tenantId.value, azureCloudContext.subscriptionId.value, AzureEnvironment.AZURE)
    F.delay(ComputeManager.authenticate(clientSecretCredential, azureProfile))
  }
}
