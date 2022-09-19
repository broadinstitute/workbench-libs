package org.broadinstitute.dsde.workbench.azure
package mock

import cats.effect.IO
import cats.mtl.Ask
import com.azure.resourcemanager.compute.models.VirtualMachine
import com.azure.resourcemanager.resources.fluentcore.model.Accepted
import org.broadinstitute.dsde.workbench.azure.AzureVmService
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.InstanceName
import reactor.core.publisher.Mono

class FakeAzureVmService extends AzureVmService[IO] {
  override def getAzureVm(name: InstanceName, cloudContext: AzureCloudContext)(implicit
                                                                               ev: Ask[IO, TraceId]
  ): IO[Option[VirtualMachine]] = IO.pure(None)

  override def deleteAzureVm(name: InstanceName, cloudContext: AzureCloudContext, forceDeletion: Boolean)(implicit
                                                                                                          ev: Ask[IO, TraceId]
  ): IO[Option[Accepted[Void]]] = IO.pure(None)

  override def startAzureVm(name: InstanceName, cloudContext: AzureCloudContext)(implicit ev: Ask[IO, TraceId]): IO[Option[Mono[Void]]] = IO.pure(None)

  override def stopAzureVm(name: InstanceName, cloudContext: AzureCloudContext)(implicit ev: Ask[IO, TraceId]): IO[Option[Mono[Void]]] = IO.pure(None)
}

object FakeAzureVmService extends FakeAzureVmService
