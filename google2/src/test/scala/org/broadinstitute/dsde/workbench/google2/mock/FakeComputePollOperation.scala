package org.broadinstitute.dsde.workbench.google2.mock

import cats.effect.{Concurrent, IO, Timer}
import cats.mtl.Ask
import com.google.cloud.compute.v1.Operation
import org.broadinstitute.dsde.workbench.google2.{ComputePollOperation, OperationName, RegionName, ZoneName}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class MockComputePollOperation(implicit override val timer: Timer[IO], override val F: Concurrent[IO])
    extends ComputePollOperation[IO] {
  override def getZoneOperation(project: GoogleProject, zoneName: ZoneName, operationName: OperationName)(
    implicit ev: Ask[IO, TraceId]
  ): IO[Operation] =
    IO.pure(Operation.newBuilder().setId("op").setName("opName").setTargetId("target").setStatus("DONE").build())

  override def getRegionOperation(project: GoogleProject, regionName: RegionName, operationName: OperationName)(
    implicit ev: Ask[IO, TraceId]
  ): IO[Operation] =
    IO.pure(Operation.newBuilder().setId("op").setName("opName").setTargetId("target").setStatus("DONE").build())

  override def getGlobalOperation(project: GoogleProject, operationName: OperationName)(
    implicit ev: Ask[IO, TraceId]
  ): IO[Operation] =
    IO.pure(Operation.newBuilder().setId("op").setName("opName").setTargetId("target").setStatus("DONE").build())
}
