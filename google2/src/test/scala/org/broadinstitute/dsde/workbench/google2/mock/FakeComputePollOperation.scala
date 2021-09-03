package org.broadinstitute.dsde.workbench.google2.mock

import cats.effect.IO
import cats.effect.kernel.Async
import cats.mtl.Ask
import com.google.cloud.compute.v1.Operation
import org.broadinstitute.dsde.workbench.google2.{ComputePollOperation, OperationName, RegionName, ZoneName}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class MockComputePollOperation(implicit override val F: Async[IO]) extends ComputePollOperation[IO] {
  override def getZoneOperation(project: GoogleProject, zoneName: ZoneName, operationName: OperationName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] =
    IO.pure(
      Operation
        .newBuilder()
        .setId(123)
        .setName("opName")
        .setTargetId(258165385)
        .setStatus(Operation.Status.DONE)
        .build()
    )

  override def getRegionOperation(project: GoogleProject, regionName: RegionName, operationName: OperationName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] =
    IO.pure(
      Operation
        .newBuilder()
        .setId(123)
        .setName("opName")
        .setTargetId(258165385)
        .setStatus(Operation.Status.DONE)
        .build()
    )

  override def getGlobalOperation(project: GoogleProject, operationName: OperationName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] =
    IO.pure(
      Operation
        .newBuilder()
        .setId(123)
        .setName("opName")
        .setTargetId(258165385)
        .setStatus(Operation.Status.DONE)
        .build()
    )
}
