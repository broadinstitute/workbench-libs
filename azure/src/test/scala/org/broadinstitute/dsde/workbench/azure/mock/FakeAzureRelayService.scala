package org.broadinstitute.dsde.workbench.azure
package mock

import cats.effect.IO
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.model.TraceId

class FakeAzureRelayService extends AzureRelayService[IO] {
  override def createRelayHybridConnection(relayNamespace: RelayNamespace,
                                           hybridConnectionName: RelayHybridConnectionName,
                                           cloudContext: AzureCloudContext
  )(implicit ev: Ask[IO, TraceId]): IO[PrimaryKey] = IO.pure(PrimaryKey("fakeKey"))

  override def deleteRelayHybridConnection(relayNamespace: RelayNamespace,
                                           hybridConnectionName: RelayHybridConnectionName,
                                           cloudContext: AzureCloudContext
  )(implicit ev: Ask[IO, TraceId]): IO[Unit] = IO.unit
}

object FakeAzureRelayService extends FakeAzureRelayService
