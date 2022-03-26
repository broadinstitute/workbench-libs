package org.broadinstitute.dsde.workbench.google2
package mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.api.core.ApiFuture
import com.google.api.gax.longrunning.{OperationFuture, OperationSnapshot}
import com.google.api.gax.retrying.RetryingFuture
import com.google.cloud.compute.v1._
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import java.util.concurrent.{Executor, TimeUnit}

class MockGoogleDiskService extends GoogleDiskService[IO] {
  override def createDisk(project: GoogleProject, zone: ZoneName, disk: Disk)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[OperationFuture[Operation, Operation]]] = IO.pure(Some(new FakeOperationFuture))

  override def deleteDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[OperationFuture[Operation, Operation]]] =
    IO.pure(
      Some(
        new FakeOperationFuture
      )
    )

  override def getDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Disk]] = IO.pure(None)

  override def listDisks(project: GoogleProject, zone: ZoneName)(implicit
    ev: Ask[IO, TraceId]
  ): Stream[IO, Disk] = Stream(Disk.newBuilder().setName("disk").build())

  override def resizeDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName, newSizeGb: Int)(implicit
    ev: Ask[IO, TraceId]
  ): IO[OperationFuture[Operation, Operation]] = IO.pure(
    new FakeOperationFuture
  )
}

object MockGoogleDiskService extends MockGoogleDiskService

class FakeOperationFuture extends OperationFuture[Operation, Operation] {
  override def getName: String = ???

  override def getInitialFuture: ApiFuture[OperationSnapshot] = ???

  override def getPollingFuture: RetryingFuture[OperationSnapshot] = ???

  override def peekMetadata(): ApiFuture[Operation] = ???

  override def getMetadata: ApiFuture[Operation] = ???

  override def addListener(listener: Runnable, executor: Executor): Unit = ???

  override def cancel(mayInterruptIfRunning: Boolean): Boolean = ???

  override def isCancelled: Boolean = ???

  override def isDone: Boolean = true

  override def get(): Operation = Operation
    .newBuilder()
    .setId(123)
    .setHttpErrorStatusCode(200)
    .setName("opName")
    .setTargetId(258165385)
    .build()

  override def get(timeout: Long, unit: TimeUnit): Operation = ???
}
