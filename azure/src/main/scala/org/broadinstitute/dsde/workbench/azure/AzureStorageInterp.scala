package org.broadinstitute.dsde.workbench.azure

import java.io.{InputStream, OutputStream}

import cats.effect.Async
import com.azure.storage.blob.models.{BlobItem, ListBlobsOptions}
import com.azure.storage.blob.{BlobClient, BlobContainerClient, BlobContainerClientBuilder}
import cats.implicits._
import java.time.Duration

import org.broadinstitute.dsde.workbench.util2.tracedLogging
import fs2.{Pipe, Stream}
import java.nio.file.Path

import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.model.TraceId

import scala.jdk.CollectionConverters._
import org.typelevel.log4cats.StructuredLogger

class AzureStorageInterp[F[_]](config: AzureStorageConfig)(implicit val F: Async[F], logger: StructuredLogger[F])
    extends AzureStorageService[F] {

  override def listObjects(connectionString: ConnectionString,
                           containerName: ContainerName,
                           opts: Option[ListBlobsOptions]
  )(implicit
    ev: Ask[F, TraceId]
  ): Stream[F, BlobItem] =
    for {
      containerClient <- Stream.eval(buildContainerClient(connectionString, containerName))
      pages <- Stream.eval(opts.fold {
        tracedLogging(F.delay(containerClient.listBlobs()), s"com.azure.storage.blob.BlobContainerClient.listBlobs()")
      } { opts =>
        tracedLogging(
          F.delay(containerClient.listBlobs(opts, Duration.ofMillis(config.listTimeout.toMillis))),
          s"com.azure.storage.blob.BlobContainerClient($connectionString, $containerName).listBlobs($opts,${config.listTimeout}"
        )
      })
      resp <- Stream.fromIterator[F](pages.iterator().asScala, 1024)
    } yield resp

  // See below article for more info on the code that can be used for uploading as a stream
  // https://docs.microsoft.com/en-us/java/api/overview/azure/storage-blob-readme?view=azure-java-stable#upload-data-to-a-blob
  override def uploadBlob(connectionString: ConnectionString, containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[F, TraceId]
  ): Pipe[F, Byte, Unit] = {
    val outputStream = for {
      blobClient <- buildBlobClient(connectionString, containerName, blobName)
      outputStream <- F.delay(blobClient.getBlockBlobClient.getBlobOutputStream)
      // This is a subclass of OutputStream, but the scala code is not happy without the explicit conversion since its a java subclass
    } yield outputStream: OutputStream
    fs2.io.writeOutputStream(outputStream, closeAfterUse = true)
  }

  // See below article for more info on the code that can be used for downloading as a stream
  // https://docs.microsoft.com/en-us/java/api/overview/azure/storage-blob-readme?view=azure-java-stable#download-a-blob-to-a-stream
  override def downloadBlob(connectionString: ConnectionString,
                            containerName: ContainerName,
                            blobName: BlobName,
                            path: Path,
                            overwrite: Boolean
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      blobClient <- buildBlobClient(connectionString, containerName, blobName)
      _ <- tracedLogging(
        F.delay(blobClient.downloadToFile(path.toAbsolutePath.toString, overwrite)),
        s"com.azure.storage.blob.BlobClient($connectionString, $containerName, $blobName).downloadToFile(${path.toAbsolutePath.toString}, overwrite=$overwrite)"
      )

    } yield ()

  override def getBlob(connectionString: ConnectionString, containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[F, TraceId]
  ): Stream[F, Byte] =
    for {
      client <- Stream.eval(
        buildBlobClient(connectionString, containerName, blobName)
      )
      is <- fs2.io.readInputStream(
        F.delay(client.openInputStream(): InputStream),
        1024,
        closeAfterUse = true
      )
    } yield is

  override def createContainer(connectionString: ConnectionString, containerName: ContainerName)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      client <- buildContainerClient(connectionString, containerName)
      _ <- tracedLogging(F.delay(client.create()),
                         s"com.azure.storage.blob.BlobContainerClient($connectionString, $containerName).create()"
      )
    } yield ()

  override def deleteContainer(connectionString: ConnectionString, containerName: ContainerName)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      client <- buildContainerClient(connectionString, containerName)
      _ <- tracedLogging(F.delay(client.delete()),
                         s"com.azure.storage.blob.BlobContainerClient($connectionString, $containerName).delete()"
      )
    } yield ()

  private def buildContainerClient(connectionString: ConnectionString,
                                   containerName: ContainerName
  ): F[BlobContainerClient] =
    F.delay(
      new BlobContainerClientBuilder()
        .connectionString(connectionString.value)
        .containerName(containerName.value)
        .buildClient()
    )

  private def buildBlobClient(connectionString: ConnectionString,
                              containerName: ContainerName,
                              blobName: BlobName
  ): F[BlobClient] =
    for {
      containerClient <- buildContainerClient(connectionString, containerName)
      blobClient <- F.delay(containerClient.getBlobClient(blobName.value))
    } yield blobClient

}
