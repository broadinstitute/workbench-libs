package org.broadinstitute.dsde.workbench.azure.mock

import java.io.{FileOutputStream, OutputStream}
import java.nio.file.Path

import cats.effect.IO
import cats.mtl.Ask
import com.azure.storage.blob.models.{BlobItem, ListBlobsOptions}
import fs2.Pipe
import fs2.Stream
import org.broadinstitute.dsde.workbench.azure.{AzureStorageService, BlobName, ConnectionString, ContainerName}
import org.broadinstitute.dsde.workbench.model.TraceId

class FakeAzureStorageService extends AzureStorageService[IO] {
  override def listObjects(connectionString: ConnectionString,
                           containerName: ContainerName,
                           opts: Option[ListBlobsOptions]
  )(implicit ev: Ask[IO, TraceId]): fs2.Stream[IO, BlobItem] = Stream.eval(IO(new BlobItem()))

  override def uploadBlob(connectionString: ConnectionString, containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[IO, TraceId]
  ): Pipe[IO, Byte, Unit] = _ => Stream.eval(IO.pure())

  override def downloadBlob(connectionString: ConnectionString,
                            containerName: ContainerName,
                            blobName: BlobName,
                            path: Path,
                            overwrite: Boolean
  )(implicit
    ev: Ask[IO, TraceId]
  ): IO[Unit] = IO.pure()

  override def getBlob(connectionString: ConnectionString, containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[IO, TraceId]
  ): fs2.Stream[IO, Byte] = Stream.eval(IO(100.toByte))

  override def createContainer(connectionString: ConnectionString, containerName: ContainerName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Unit] = IO.pure()

  override def deleteContainer(connectionString: ConnectionString, containerName: ContainerName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Unit] = IO.pure()
}
