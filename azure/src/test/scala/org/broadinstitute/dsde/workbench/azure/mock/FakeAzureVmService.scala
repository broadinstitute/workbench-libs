package org.broadinstitute.dsde.workbench.azure
package mock

import cats.effect.IO
import cats.mtl.Ask
import com.azure.resourcemanager.compute.models.VirtualMachine
import org.broadinstitute.dsde.workbench.azure.AzureVmService
import org.broadinstitute.dsde.workbench.model.TraceId

class FakeAzureVmService extends AzureVmService[IO] {
  override def getAzureVm(name: String, cloudContext: AzureCloudContext)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[VirtualMachine]] = IO.pure(None)
}

object FakeAzureVmService extends FakeAzureVmService
