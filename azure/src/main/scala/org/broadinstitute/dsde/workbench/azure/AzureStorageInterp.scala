package org.broadinstitute.dsde.workbench.azure

import cats.effect.Async
import cats.implicits._
import cats.mtl.Ask
import com.azure.core.util.Context
import com.azure.storage.blob.models.{BlobItem, ListBlobsOptions}
import com.azure.storage.blob.{BlobClient, BlobContainerClient}
import fs2.{Pipe, Stream}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.{tracedLogging, RemoveObjectResult}
import org.typelevel.log4cats.StructuredLogger

import java.io.{InputStream, OutputStream}
import java.nio.file.Path
import java.time.Duration
import scala.jdk.CollectionConverters._

class AzureStorageInterp[F[_]](config: AzureStorageConfig, containerClients: Map[ContainerName, BlobContainerClient])(
  implicit
  val F: Async[F],
  logger: StructuredLogger[F]
) extends AzureStorageService[F] {

  override def listObjects(containerName: ContainerName, opts: Option[ListBlobsOptions])(implicit
    ev: Ask[F, TraceId]
  ): Stream[F, BlobItem] =
    for {
      containerClient <- Stream.eval(getContainerClient(containerName))
      pages <- Stream.eval(opts.fold {
        tracedLogging(F.blocking(containerClient.listBlobs()),
                      s"com.azure.storage.blob.BlobContainerClient.listBlobs()"
        )
      } { opts =>
        tracedLogging(
          F.blocking(containerClient.listBlobs(opts, Duration.ofMillis(config.listTimeout.toMillis))),
          s"com.azure.storage.blob.BlobContainerClient($containerName).listBlobs($opts,${config.listTimeout}"
        )
      })
      resp <- Stream.fromIterator[F](pages.iterator().asScala, 1024)
    } yield resp

  // See below article for more info on the code that can be used for uploading as a stream
  // https://docs.microsoft.com/en-us/java/api/overview/azure/storage-blob-readme?view=azure-java-stable#upload-data-to-a-blob
  override def uploadBlob(containerName: ContainerName, blobName: BlobName, overwrite: Boolean)(implicit
    ev: Ask[F, TraceId]
  ): Pipe[F, Byte, Unit] = {
    val outputStream = for {
      blobClient <- buildBlobClient(containerName, blobName)
      outputStream <- F.blocking(blobClient.getBlockBlobClient.getBlobOutputStream(overwrite))
      // This is a subclass of OutputStream, but the scala code is not happy without the explicit conversion since its a java subclass
    } yield outputStream: OutputStream
    fs2.io.writeOutputStream(outputStream, closeAfterUse = true)
  }

  // See below article for more info on the code that can be used for downloading as a stream
  // https://docs.microsoft.com/en-us/java/api/overview/azure/storage-blob-readme?view=azure-java-stable#download-a-blob-to-a-stream
  override def downloadBlob(containerName: ContainerName, blobName: BlobName, path: Path, overwrite: Boolean)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      blobClient <- buildBlobClient(containerName, blobName)
      _ <- tracedLogging(
        F.blocking(blobClient.downloadToFile(path.toAbsolutePath.toString, overwrite)),
        s"com.azure.storage.blob.BlobClient($containerName, $blobName).downloadToFile(${path.toAbsolutePath.toString}, overwrite=$overwrite)"
      )

    } yield ()

  override def getBlob(containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[F, TraceId]
  ): Stream[F, Byte] =
    for {
      client <- Stream.eval(
        buildBlobClient(containerName, blobName)
      )
      is <- fs2.io
        .readInputStream(
          F.blocking(client.openInputStream(): InputStream),
          1024,
          closeAfterUse = true
        )
        .recoverWith {
          case t: com.azure.storage.blob.models.BlobStorageException if t.getStatusCode == 404 =>
            Stream.empty
        }
    } yield is

  override def deleteBlob(containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[F, TraceId]
  ): F[RemoveObjectResult] =
    for {
      traceId <- ev.ask
      client <- buildBlobClient(containerName, blobName)
      resp <- tracedLogging(
        F.blocking(
          client
            .deleteWithResponse(
              null,
              null,
              Duration.ofMillis(config.generalTimeout.toMillis),
              new Context("traceId", traceId)
            )
            .getStatusCode
        ).map(code => RemoveObjectResult(code == 202))
          .handleErrorWith {
            case e: com.azure.storage.blob.models.BlobStorageException if e.getStatusCode == 404 =>
              F.pure(RemoveObjectResult(false))
            case e => F.raiseError(e)
          },
        s"com.azure.storage.blob.BlobClient($containerName, $blobName).deleteWithResponse(null, null, ${Duration
            .ofMillis(config.generalTimeout.toMillis)}, Context('traceId', $traceId))"
      )
    } yield resp

  private def getContainerClient(containerName: ContainerName): F[BlobContainerClient] =
    F.fromOption(containerClients.get(containerName), new RuntimeException(s"no client for ${containerName} found"))

  private def buildBlobClient(containerName: ContainerName, blobName: BlobName): F[BlobClient] =
    for {
      containerClient <- getContainerClient(containerName)
      blobClient <- F.blocking(containerClient.getBlobClient(blobName.value))
    } yield blobClient

}
