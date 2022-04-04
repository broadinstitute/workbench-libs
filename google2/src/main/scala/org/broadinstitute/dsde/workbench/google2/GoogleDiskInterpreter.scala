package org.broadinstitute.dsde.workbench.google2

import cats.effect.Async
import cats.effect.std.Semaphore
import cats.mtl.Ask
import cats.syntax.all._
import com.google.api.gax.longrunning.{OperationFuture, OperationSnapshot}
import com.google.api.gax.retrying.RetryingFuture
import com.google.cloud.compute.v1._
import fs2.Stream
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import scala.collection.JavaConverters._

private[google2] class GoogleDiskInterpreter[F[_]: StructuredLogger](
  diskClient: DisksClient,
  retryConfig: RetryConfig,
  blockerBound: Semaphore[F]
)(implicit F: Async[F])
    extends GoogleDiskService[F] {

  override def createDisk(project: GoogleProject, zone: ZoneName, disk: Disk)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]] =
    retryF(
      recoverF(F.blocking(diskClient.insertAsync(project.value, zone.value, disk)), whenStatusCode(409)),
      s"com.google.cloud.compute.v1DiskClient.insertDisk(${project.value}, ${zone.value}, ${disk.getName})"
    ).compile.lastOrError

  def getDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Disk]] = {
    val fa = recoverF(F.blocking(diskClient.get(project.value, zone.value, diskName.value)), whenStatusCode(404))

    ev.ask.flatMap(traceId =>
      withLogging(
        fa,
        Some(traceId),
        s"com.google.cloud.compute.v1.DiskClient.getDisk(${project.value}, ${zone.value}, ${diskName.value})"
      )
    )
  }

  override def deleteDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]] = {
    val fa =
      recoverF(F.blocking(diskClient.deleteAsync(project.value, zone.value, diskName.value)), whenStatusCode(404))

    ev.ask.flatMap(traceId =>
      withLogging(
        fa,
        Some(traceId),
        s"com.google.cloud.compute.v1.DiskClient.deleteDisk(${project.value}, ${zone.value}, ${diskName.value})"
      )
    )
  }

  override def listDisks(project: GoogleProject, zone: ZoneName)(implicit
    ev: Ask[F, TraceId]
  ): Stream[F, Disk] =
    for {
      pagedResults <- retryF(
        F.blocking(diskClient.list(project.value, zone.value)),
        s"com.google.cloud.compute.v1.DiskClient.listDisks(${project.value}, ${zone.value})"
      )

      res <- Stream.fromIterator[F](pagedResults.iterateAll().iterator().asScala, 1024)
    } yield res

  override def resizeDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName, newSizeGb: Int)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]] = {
    val request = DisksResizeRequest.newBuilder().setSizeGb(newSizeGb).build()
    retryF(
      F.blocking(diskClient.resizeAsync(project.value, zone.value, diskName.value, request)),
      s"com.google.cloud.compute.v1.DiskClient.resizeDisk(${project.value}, ${zone.value}, ${diskName.value}, $newSizeGb)"
    ).compile.lastOrError
  }

  private def retryF[A](fa: F[A], loggingMsg: String)(implicit ev: Ask[F, TraceId]): Stream[F, A] =
    tracedRetryF(retryConfig)(blockerBound.permit.use(_ => fa), loggingMsg)

}
