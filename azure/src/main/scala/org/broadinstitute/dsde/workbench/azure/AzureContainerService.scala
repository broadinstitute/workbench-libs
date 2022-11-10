package org.broadinstitute.dsde.workbench.azure

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.resourcemanager.containerservice.models.KubernetesCluster
import io.kubernetes.client.openapi.models.V1Status
import io.kubernetes.client.proto.V1.{Pod, PodStatus}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger

trait AzureContainerService[F[_]] {

  /** Gets an AKS cluster by name. */
  def getCluster(name: AKSClusterName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[KubernetesCluster]

  /** Gets credentials used to connect to an AKS cluster. */
  def getClusterCredentials(name: AKSClusterName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[AKSCredentials]

  /** Lists pods in a AKS cluster's namespace. */
  def listNamespacePods(name: AKSClusterName, namespace: String, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[List[io.kubernetes.client.openapi.models.V1Pod]]

  /** Deletes a AKS namespace. */
  def deleteNamespace(name: AKSClusterName, namespace: String, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[io.kubernetes.client.openapi.models.V1Status]

}

object AzureContainerService {
  def fromAzureAppRegistrationConfig[F[_]: Async: StructuredLogger](
    azureAppRegistrationConfig: AzureAppRegistrationConfig
  ): Resource[F, AzureContainerService[F]] = {
    val clientSecretCredential = new ClientSecretCredentialBuilder()
      .clientId(azureAppRegistrationConfig.clientId.value)
      .clientSecret(azureAppRegistrationConfig.clientSecret.value)
      .tenantId(azureAppRegistrationConfig.managedAppTenantId.value)
      .build
    Resource.eval(Async[F].pure(new AzureContainerServiceInterp(clientSecretCredential)))
  }
}
