package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.ApplicativeAsk
import cats.implicits._
import com.google.cloud.compute.v1.{Disk, DiskClient, DisksResizeRequest, ListDisksHttpRequest, Operation, ProjectZoneDiskName, ProjectZoneName}
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.collection.JavaConverters._

private[google2] class GoogleDiskInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift] (
  diskClient: DiskClient,
  retryConfig: RetryConfig,
  blocker: Blocker,
  blockerBound: Semaphore[F]
) extends GoogleDiskService[F] {

  override def createDisk(project: GoogleProject, zone: ZoneName, disk: Disk)
    (implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectZone = ProjectZoneName.of(project.value, zone.value)
    retryF(
      Async[F].delay(diskClient.insertDisk(projectZone, disk)),
      s"com.google.cloud.compute.v1DiskClient.insertDisk(${projectZone.toString}, ${disk.getName})"
    ).compile.lastOrError
  }

  override def deleteDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectZoneDiskName = ProjectZoneDiskName.of(diskName.value, project.value, zone.value)
    retryF(
      Async[F].delay(diskClient.deleteDisk(projectZoneDiskName)),
      s"com.google.cloud.compute.v1.DiskClient.deleteDisk(${projectZoneDiskName.toString})"
    ).compile.lastOrError
  }

  override def listDisks(project: GoogleProject, zone: ZoneName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): Stream[F, Disk] = {
    val projectZone = ProjectZoneName.of(project.value, zone.value)
    for {
      firstPageResults <- retryF(
        Async[F].delay(diskClient.listDisks(projectZone)),
        s"com.google.cloud.compute.v1.DiskClient.listDisks(${projectZone.toString})"
      )
      zoneString = s"${project.value}/zones/${zone.value}"
      _ = println("firstPage: " + firstPageResults.toString)
      nextPage <- Stream.unfoldEval(firstPageResults) { currentPage =>
        println("currentPage: " + currentPage.getNextPageToken)
        val tokenOpt = if (currentPage.getNextPageToken.isEmpty) None else Some(currentPage.getNextPageToken)
        tokenOpt.traverse { token =>
          val request = ListDisksHttpRequest.newBuilder()
            .setZone(zoneString)
            .setPageToken(token)
            .build()
          val response = retryF(
            Async[F].delay(diskClient.listDisks(request)),
            s"com.google.cloud.compute.v1.DiskClient.listDisks(${token})"
          )
          println("response: " + response.toString())
          response.compile.lastOrError.map(next => (currentPage, next))
        }
      }
      res <- Stream.fromIterator[F](nextPage.iterateAll().iterator().asScala)
      _ = println("res: " + res.toString)
    } yield res
  }

  override def resizeDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName, newSizeGb: Int)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectZoneDiskName = ProjectZoneDiskName.of(diskName.value, project.value, zone.value)
    val request = DisksResizeRequest.newBuilder().setSizeGb(newSizeGb.toString).build()
    retryF(
      Async[F].delay(diskClient.resizeDisk(projectZoneDiskName, request)),
      s"com.google.cloud.compute.v1.DiskClient.resizeDisk(${projectZoneDiskName.toString}, $newSizeGb)"
    ).compile.lastOrError
  }

  private def retryF[A](fa: F[A], loggingMsg: String)(implicit ev: ApplicativeAsk[F, TraceId]): Stream[F, A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(blocker.blockOn(fa)), loggingMsg)

}
