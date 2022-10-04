package org.broadinstitute.dsde.workbench.azure.mock

import cats.effect.IO
import cats.mtl.Ask
import com.azure.resourcemanager.containerservice.models.KubernetesCluster
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
}
