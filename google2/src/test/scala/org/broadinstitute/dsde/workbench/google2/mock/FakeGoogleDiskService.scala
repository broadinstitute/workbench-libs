package org.broadinstitute.dsde.workbench.google2
package mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.api.core.ApiFuture
import com.google.api.gax.longrunning.{OperationFuture, OperationSnapshot}
import com.google.api.gax.retrying.RetryingFuture
import com.google.cloud.compute.v1._
import com.google.cloud.dataproc.v1.{Cluster, ClusterOperationMetadata}
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import java.util.concurrent.{Executor, TimeUnit}
import com.google.protobuf.Empty

class MockGoogleDiskService extends GoogleDiskService[IO] {
  override def createDisk(project: GoogleProject, zone: ZoneName, disk: Disk)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[OperationFuture[Operation, Operation]]] = IO.pure(Some(new FakeComputeOperationFuture))

  override def deleteDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[OperationFuture[Operation, Operation]]] =
    IO.pure(
      Some(
        new FakeComputeOperationFuture
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
    new FakeComputeOperationFuture
  )
}

object MockGoogleDiskService extends MockGoogleDiskService

//BaseOperation
class BaseFakeOperationFuture[A, B] extends OperationFuture[A, B] {
  override def getName: String = "op"

  override def getInitialFuture: ApiFuture[OperationSnapshot] = ???

  override def getPollingFuture: RetryingFuture[OperationSnapshot] = ???

  override def peekMetadata(): ApiFuture[B] = ???

  override def getMetadata: ApiFuture[B] = ???

  override def addListener(listener: Runnable, executor: Executor): Unit = ???

  override def cancel(mayInterruptIfRunning: Boolean): Boolean = ???

  override def isCancelled: Boolean = ???

  override def isDone: Boolean = true

  override def get(timeout: Long, unit: TimeUnit): A = ???

  override def get(): A = ???
}

class FakeComputeOperationFuture extends BaseFakeOperationFuture[Operation, Operation] {
  override def get(timeout: Long, unit: TimeUnit): Operation = ???

  override def get(): Operation =
    Operation
      .newBuilder()
      .setId(123)
      .setHttpErrorStatusCode(200)
      .setName("opName")
      .setTargetId(258165385)
      .build()
}

class FakeDataprocEmptyOperationFutureOp extends BaseFakeOperationFuture[Empty, ClusterOperationMetadata] {
  override def get(timeout: Long, unit: TimeUnit): Empty = ???

  override def get(): Empty = Empty.newBuilder().build()
}

class FakeDataprocClusterOperationFutureOp extends BaseFakeOperationFuture[Cluster, ClusterOperationMetadata] {
  override def get(timeout: Long, unit: TimeUnit): Cluster = ???

  override def get(): Cluster = Cluster.newBuilder().build()
}
