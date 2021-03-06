package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.mtl.Ask
import com.google.cloud.compute.v1._
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class MockGoogleDiskService extends GoogleDiskService[IO] {
  override def createDisk(project: GoogleProject, zone: ZoneName, disk: Disk)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Operation]] =
    IO.pure(Some(Operation.newBuilder().setId("op").setName("opName").setTargetId("target").build()))

  override def deleteDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Operation]] =
    IO.pure(Some(Operation.newBuilder().setId("op").setName("opName").setTargetId("target").build()))

  override def getDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Disk]] = IO.pure(None)

  override def listDisks(project: GoogleProject, zone: ZoneName)(implicit
    ev: Ask[IO, TraceId]
  ): Stream[IO, Disk] = Stream(Disk.newBuilder().setName("disk").build())

  override def resizeDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName, newSizeGb: Int)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] = IO.pure(Operation.newBuilder().setId("op").setName("opName").setTargetId("target").build())
}

object MockGoogleDiskService extends MockGoogleDiskService
