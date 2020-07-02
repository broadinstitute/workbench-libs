package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, Concurrent, ContextShift, Timer}
import cats.mtl.ApplicativeAsk
import com.google.cloud.compute.v1._
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class ComputePollOperationInterpreter[F[_]: StructuredLogger: ContextShift](
  zoneOperationClient: ZoneOperationClient,
  regionOperationClient: RegionOperationClient,
  globalOperationClient: GlobalOperationClient,
  blocker: Blocker,
  blockerBound: Semaphore[F]
)(implicit override val F: Concurrent[F], override val timer: Timer[F])
    extends ComputePollOperation[F] {
  override def getZoneOperation(project: GoogleProject, zoneName: ZoneName, operationName: OperationName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val request = ProjectZoneOperationName
      .newBuilder()
      .setProject(project.value)
      .setZone(zoneName.value)
      .setOperation(operationName.value)
      .build
    tracedLogging(
      blockOn(F.delay(zoneOperationClient.getZoneOperation(request))),
      s"com.google.cloud.compute.v1.ZoneOperationClient.getZoneOperation(${request.toString})"
    )
  }

  override def getRegionOperation(project: GoogleProject, regionName: RegionName, operationName: OperationName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val request = ProjectRegionOperationName
      .newBuilder()
      .setProject(project.value)
      .setRegion(regionName.value)
      .setOperation(operationName.value)
      .build
    tracedLogging(
      blockOn(F.delay(regionOperationClient.getRegionOperation(request))),
      s"com.google.cloud.compute.v1.regionOperationClient.getRegionOperation(${request.toString})"
    )
  }

  override def getGlobalOperation(project: GoogleProject, operationName: OperationName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val request =
      ProjectGlobalOperationName.newBuilder().setProject(project.value).setOperation(operationName.value).build
    tracedLogging(
      blockOn(F.delay(globalOperationClient.getGlobalOperation(request))),
      s"com.google.cloud.compute.v1.globalOperationClient.getGlobalOperation(${request.toString})"
    )
  }

  private def blockOn[A](fa: F[A]): F[A] = blockerBound.withPermit(blocker.blockOn(fa))
}
