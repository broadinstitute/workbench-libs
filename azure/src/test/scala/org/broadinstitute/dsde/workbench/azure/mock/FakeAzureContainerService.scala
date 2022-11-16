package org.broadinstitute.dsde.workbench.azure.mock

import cats.effect.IO
import cats.mtl.Ask
import com.azure.resourcemanager.containerservice.models.KubernetesCluster
import io.kubernetes.client.openapi.models.{V1Pod, V1Status}
import org.broadinstitute.dsde.workbench.azure.{
  AKSCertificate,
  AKSClusterName,
  AKSCredentials,
  AKSServer,
  AKSToken,
  AzureCloudContext,
  AzureContainerService
}
import org.broadinstitute.dsde.workbench.model.TraceId

class FakeAzureContainerService extends AzureContainerService[IO] {
  override def getClusterCredentials(name: AKSClusterName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[IO, TraceId]
  ): IO[AKSCredentials] = IO.pure(AKSCredentials(AKSServer("server"), AKSToken("token"), AKSCertificate("cert")))

  override def getCluster(name: AKSClusterName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[IO, TraceId]
  ): IO[KubernetesCluster] =
    IO.raiseError(new NotImplementedError())

  /** Lists pods in a AKS cluster's namespace. */
  override def listNamespacePods(name: AKSClusterName, namespace: String, cloudContext: AzureCloudContext)(implicit
    ev: Ask[IO, TraceId]
  ): IO[List[V1Pod]] =
    IO.raiseError(new NotImplementedError())

  /** Deletes a AKS namespace. */
  override def deleteNamespace(name: AKSClusterName, namespace: String, cloudContext: AzureCloudContext)(implicit
    ev: Ask[IO, TraceId]
  ): IO[V1Status] =
    IO.raiseError(new NotImplementedError())
}
