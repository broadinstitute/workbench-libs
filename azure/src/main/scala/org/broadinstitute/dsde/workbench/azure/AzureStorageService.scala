package org.broadinstitute.dsde.workbench.azure

import java.nio.file.Path
import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.azure.storage.blob.{BlobContainerClientBuilder, BlobServiceClient, BlobServiceClientBuilder}
import com.azure.storage.blob.models.{BlobItem, ListBlobsOptions}
import fs2.{Pipe, Stream}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger
import cats.implicits._
import org.broadinstitute.dsde.workbench.util2.RemoveObjectResult

import scala.concurrent.duration.FiniteDuration

trait AzureStorageService[F[_]] {
  def listObjects(containerName: ContainerName, opts: Option[ListBlobsOptions])(implicit
    ev: Ask[F, TraceId]
  ): Stream[F, BlobItem]

  def uploadBlob(containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[F, TraceId]
  ): Pipe[F, Byte, Unit]

  def downloadBlob(containerName: ContainerName, blobName: BlobName, path: Path, overwrite: Boolean)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  def getBlob(containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[F, TraceId]
  ): Stream[F, Byte]

  def deleteBlob(containerName: ContainerName, blobName: BlobName)(implicit
    ev: Ask[F, TraceId]
  ): F[RemoveObjectResult]
}

object AzureStorageService {
  // Note SAS token has expiration time. If the token expires, the client will not work
  def fromSasToken[F[_]: Async: StructuredLogger](
    azureStorageConfig: AzureStorageConfig,
    azureStorageConfigs: Map[ContainerName, ContainerAuthConfig]
  ): Resource[F, AzureStorageService[F]] =
    Resource.eval(
      Async[F]
        .delay(azureStorageConfigs.map { case (containerName, config) =>
          val client = new BlobContainerClientBuilder()
            .sasToken(config.sasToken.value)
            .endpoint(config.endpointUrl.value)
            .containerName(containerName.value)
            .buildClient()

          containerName -> client
        })
        .map(mp => new AzureStorageInterp(azureStorageConfig, mp))
    )
}

final case class AzureStorageConfig(generalTimeout: FiniteDuration, listTimeout: FiniteDuration)
final case class ContainerAuthConfig(sasToken: SasToken, endpointUrl: EndpointUrl)

final case class SasToken(value: String) extends AnyVal
final case class EndpointUrl(value: String) extends AnyVal

// See this article for more information on connection strings: https://docs.microsoft.com/en-us/azure/storage/blobs/storage-quickstart-blobs-java?tabs=powershell%2Cenvironment-variable-windows#get-the-connection-string
final case class ConnectionString(value: String) extends AnyVal
// analogous to google container name
final case class ContainerName(value: String) extends AnyVal
final case class BlobName(value: String) extends AnyVal
