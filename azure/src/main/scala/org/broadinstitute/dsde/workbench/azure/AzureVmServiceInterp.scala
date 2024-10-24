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
import com.azure.resourcemanager.resources.fluentcore.model.Accepted
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.{tracedLogging, InstanceName}
import org.typelevel.log4cats.StructuredLogger
import reactor.core.publisher.Mono

class AzureVmServiceInterp[F[_]](clientSecretCredential: ClientSecretCredential)(implicit
  val F: Async[F],
  logger: StructuredLogger[F]
) extends AzureVmService[F] {

  def getAzureVm(name: InstanceName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[VirtualMachine]] =
    for {
      azureComputeManager <- buildComputeManager(cloudContext)

      fa = F
        .blocking(
          azureComputeManager
            .virtualMachines()
            .getByResourceGroup(cloudContext.managedResourceGroupName.value, name.value)
        )
        .map(Option(_))
        .handleErrorWith {
          case e: ManagementException
              if e.getValue.getCode().equals("ResourceNotFound") | e.getValue.getCode().equals("AuthorizationFailed") =>
            F.pure(none[VirtualMachine])
          case e => F.raiseError[Option[VirtualMachine]](e)
        }
      res <- tracedLogging(
        fa,
        s"com.azure.resourcemanager.resources.fluentcore.arm.collection.SupportsGettingByResourceGroup.getByResourceGroup(${cloudContext.managedResourceGroupName.value}, ${name})"
      )
    } yield res

  def deleteAzureVm(name: InstanceName, cloudContext: AzureCloudContext, forceDeletion: Boolean)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Accepted[Void]]] =
    for {
      azureComputeManager <- buildComputeManager(cloudContext)
      fa = F
        .blocking(
          azureComputeManager
            .virtualMachines()
            .beginDeleteByResourceGroup(cloudContext.managedResourceGroupName.value,
                                        name.value,
                                        forceDeletion
            ) // Begins force deleting a virtual machine from Azure
        )
        .map(Option(_))
        .handleErrorWith {
          case e: ManagementException
              if e.getValue.getCode().equals("ResourceNotFound") | e.getValue.getCode().equals("AuthorizationFailed") =>
            F.pure(none[Accepted[Void]])
          case e => F.raiseError[Option[Accepted[Void]]](e)
        }
      res <- tracedLogging(
        fa,
        s"com.azure.resourcemanager.compute.models.VirtualMachines.beginDeleteByResourceGroup(${cloudContext.managedResourceGroupName.value}, ${name}, ${forceDeletion})"
      )
    } yield res

  def startAzureVm(name: InstanceName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Mono[Void]]] = for {
    azureComputeManager <- buildComputeManager(cloudContext)
    fa = F
      .blocking(
        azureComputeManager
          .virtualMachines()
          .startAsync(cloudContext.managedResourceGroupName.value, name.value)
      )
      .map(Option(_))
      .handleErrorWith {
        case e: ManagementException
            if e.getValue.getCode().equals("ResourceNotFound") | e.getValue.getCode().equals("AuthorizationFailed") =>
          F.pure(none[Mono[Void]])
        case e => F.raiseError[Option[Mono[Void]]](e)
      }
    res <- tracedLogging(
      fa,
      s"com.azure.resourcemanager.compute.models.VirtualMachines.startAsync(${cloudContext.managedResourceGroupName.value}, ${name})"
    )
  } yield res

  private def buildComputeManager(azureCloudContext: AzureCloudContext): F[ComputeManager] = {
    val azureProfile =
      new AzureProfile(azureCloudContext.tenantId.value, azureCloudContext.subscriptionId.value, AzureEnvironment.AZURE)
    F.blocking(ComputeManager.authenticate(clientSecretCredential, azureProfile))
  }

  def stopAzureVm(name: InstanceName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Mono[Void]]] = for {
    azureComputeManager <- buildComputeManager(cloudContext)
    fa = F
      .blocking(
        azureComputeManager
          .virtualMachines()
          .deallocateAsync(cloudContext.managedResourceGroupName.value, name.value)
      )
      .map(Option(_))
      .handleErrorWith {
        case e: ManagementException
            if e.getValue.getCode().equals("ResourceNotFound") | e.getValue.getCode().equals("AuthorizationFailed") =>
          F.pure(none[Mono[Void]])
        case e => F.raiseError[Option[Mono[Void]]](e)
      }
    res <- tracedLogging(
      fa,
      s"com.azure.resourcemanager.compute.models.VirtualMachines.deallocateAsync(${cloudContext.managedResourceGroupName.value}, ${name})"
    )
  } yield res
}
