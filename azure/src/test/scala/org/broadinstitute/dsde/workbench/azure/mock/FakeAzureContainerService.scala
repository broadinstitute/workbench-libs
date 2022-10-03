package org.broadinstitute.dsde.workbench.azure.mock

import cats.effect.IO
import cats.mtl.Ask
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
}
