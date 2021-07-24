package org.broadinstitute.dsde.workbench.google2

import cats.effect.Async
import cats.effect.std.Semaphore
import cats.mtl.Ask
import com.google.cloud.compute.v1._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

class ComputePollOperationInterpreter[F[_]: StructuredLogger](
  zoneOperationClient: ZoneOperationClient,
  regionOperationClient: RegionOperationClient,
  globalOperationClient: GlobalOperationClient,
  blockerBound: Semaphore[F]
)(implicit override val F: Async[F])
    extends ComputePollOperation[F] {
  override def getZoneOperation(project: GoogleProject, zoneName: ZoneName, operationName: OperationName)(implicit
    ev: Ask[F, TraceId]
  ): F[Operation] = {
    val request = ProjectZoneOperationName
      .newBuilder()
      .setProject(project.value)
      .setZone(zoneName.value)
      .setOperation(operationName.value)
      .build
    tracedLogging(
      blockOn( F.blocking(zoneOperationClient.getZoneOperation(request))),
      s"com.google.cloud.compute.v1.ZoneOperationClient.getZoneOperation(${request.toString})",
      showOperation
    )
  }

  override def getRegionOperation(project: GoogleProject, regionName: RegionName, operationName: OperationName)(implicit
    ev: Ask[F, TraceId]
  ): F[Operation] = {
    val request = ProjectRegionOperationName
      .newBuilder()
      .setProject(project.value)
      .setRegion(regionName.value)
      .setOperation(operationName.value)
      .build
    tracedLogging(
      blockOn(F.blocking(regionOperationClient.getRegionOperation(request))),
      s"com.google.cloud.compute.v1.regionOperationClient.getRegionOperation(${request.toString})",
      showOperation
    )
  }

  override def getGlobalOperation(project: GoogleProject, operationName: OperationName)(implicit
    ev: Ask[F, TraceId]
  ): F[Operation] = {
    val request =
      ProjectGlobalOperationName.newBuilder().setProject(project.value).setOperation(operationName.value).build
    tracedLogging(
      blockOn(F.blocking(globalOperationClient.getGlobalOperation(request))),
      s"com.google.cloud.compute.v1.globalOperationClient.getGlobalOperation(${request.toString})",
      showOperation
    )
  }

  private def blockOn[A](fa: F[A]): F[A] = blockerBound.permit.use(_ => fa)
}
