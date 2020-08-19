package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.cloud.compute.v1.{
  Disk,
  DiskClient,
  DisksResizeRequest,
  Operation,
  ProjectZoneDiskName,
  ProjectZoneName
}
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.collection.JavaConverters._

private[google2] class GoogleDiskInterpreter[F[_]: StructuredLogger: Timer: ContextShift](
  diskClient: DiskClient,
  retryConfig: RetryConfig,
  blocker: Blocker,
  blockerBound: Semaphore[F]
)(implicit F: Async[F])
    extends GoogleDiskService[F] {

  override def createDisk(project: GoogleProject, zone: ZoneName, disk: Disk)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectZone = ProjectZoneName.of(project.value, zone.value)
    retryF(
      F.delay(diskClient.insertDisk(projectZone, disk)),
      s"com.google.cloud.compute.v1DiskClient.insertDisk(${projectZone.toString}, ${disk.getName})"
    ).compile.lastOrError
  }

  def getDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): Stream[F, Disk] = {
    val projectZoneDiskName = ProjectZoneDiskName.of(diskName.value, project.value, zone.value)
    retryF(
      F.delay(diskClient.getDisk(projectZoneDiskName)),
      s"com.google.cloud.compute.v1DiskClient.getDisk(${projectZoneDiskName})"
    )
  }

  override def deleteDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Operation]] = {
    val projectZoneDiskName = ProjectZoneDiskName.of(diskName.value, project.value, zone.value)
    val fa = F
      .delay(diskClient.deleteDisk(projectZoneDiskName))
      .map(Option(_))
      .handleErrorWith {
        case _: com.google.api.gax.rpc.NotFoundException => F.pure(none[Operation])
        case e                                           => F.raiseError[Option[Operation]](e)
      }

    ev.ask.flatMap(
      traceId =>
        withLogging(
          fa,
          Some(traceId),
          s"com.google.cloud.compute.v1.DiskClient.deleteDisk(${projectZoneDiskName.toString})"
        )
    )
  }

  override def listDisks(project: GoogleProject, zone: ZoneName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): Stream[F, Disk] = {
    val projectZone = ProjectZoneName.of(project.value, zone.value)
    for {
      pagedResults <- retryF(
        F.delay(diskClient.listDisks(projectZone)),
        s"com.google.cloud.compute.v1.DiskClient.listDisks(${projectZone.toString})"
      )

      res <- Stream.fromIterator[F](pagedResults.iterateAll().iterator().asScala)
    } yield res
  }

  override def resizeDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName, newSizeGb: Int)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectZoneDiskName = ProjectZoneDiskName.of(diskName.value, project.value, zone.value)
    val request = DisksResizeRequest.newBuilder().setSizeGb(newSizeGb.toString).build()
    retryF(
      F.delay(diskClient.resizeDisk(projectZoneDiskName, request)),
      s"com.google.cloud.compute.v1.DiskClient.resizeDisk(${projectZoneDiskName.toString}, $newSizeGb)"
    ).compile.lastOrError
  }

  private def retryF[A](fa: F[A], loggingMsg: String)(implicit ev: ApplicativeAsk[F, TraceId]): Stream[F, A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(blocker.blockOn(fa)), loggingMsg)

}
