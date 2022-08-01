package org.broadinstitute.dsde.workbench.azure

import java.nio.file.Path

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.azure.storage.blob.models.{BlobItem, ListBlobsOptions}
import fs2.{Pipe, Stream}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger

import scala.concurrent.duration.FiniteDuration

trait AzureStorageService[F[_]] {
  def listObjects(connectionString: ConnectionString, containerName: ContainerName, opts: Option[ListBlobsOptions])(
    implicit ev: Ask[F, TraceId]
  ): Stream[F, BlobItem]

  def uploadBlob(connectionString: ConnectionString, containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[F, TraceId]
  ): Pipe[F, Byte, Unit]

  // The file cannot exist
  def downloadBlob(connectionString: ConnectionString,
                   containerName: ContainerName,
                   blobName: BlobName,
                   path: Path,
                   overwrite: Boolean
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  def getBlob(connectionString: ConnectionString, containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[F, TraceId]
  ): Stream[F, Byte]

  def createContainer(connectionString: ConnectionString, containerName: ContainerName)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  def deleteContainer(connectionString: ConnectionString, containerName: ContainerName)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]
}

object AzureStorageService {
  def fromAzureAppRegistrationConfig[F[_]: Async: StructuredLogger](
    azureStorageConfig: AzureStorageConfig
  ): Resource[F, AzureStorageService[F]] =
    Resource.eval(Async[F].pure(new AzureStorageInterp(azureStorageConfig)))
}

final case class AzureStorageConfig(listTimeout: FiniteDuration)

// See this article for more information on connection strings: https://docs.microsoft.com/en-us/azure/storage/blobs/storage-quickstart-blobs-java?tabs=powershell%2Cenvironment-variable-windows#get-the-connection-string
final case class ConnectionString(value: String) extends AnyVal
// analogous to google container name
final case class ContainerName(value: String) extends AnyVal
final case class BlobName(value: String) extends AnyVal
