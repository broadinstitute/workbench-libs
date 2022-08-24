package org.broadinstitute.dsde.workbench.azure

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Path, Paths}
import java.util.UUID

import cats.effect.{IO, Resource}
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.RemoveObjectResult
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._

final class AzureStorageManualTest(
  sasToken: String = "",
  endpointUrl: String = "",
  container: String = "testconn"
) {

  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = Slf4jLogger.getLogger[IO]

  val containerName = ContainerName(container)

  val serviceResource: Resource[IO, AzureStorageService[IO]] =
    AzureStorageService.fromSasToken(
      AzureStorageConfig(10 minutes, 10 minutes),
      Map(containerName -> ContainerAuthConfig(SasToken(sasToken), EndpointUrl(endpointUrl)))
    )

  def useService(fa: AzureStorageService[IO] => IO[Unit]) =
    serviceResource.use { service =>
      fa(service)
    }

  val defaultBlobName = "testblob"

  def uploadBlob(uploadString: String = "contents", blobName: String = defaultBlobName): IO[Unit] =
    serviceResource.use { s =>
      fs2.Stream
        .emits(uploadString.getBytes(Charset.forName("UTF-8")))
        .covary[IO]
        .through(s.uploadBlob(containerName, BlobName(blobName)))
        .compile
        .drain
    }

  def deleteBlob(blobName: String): IO[RemoveObjectResult] =
    serviceResource.use { s =>
      s.deleteBlob(containerName, BlobName(blobName))
    }

  def listBlob(): IO[List[String]] =
    serviceResource.use { s =>
      s.listObjects(containerName, None)
        .compile
        .toList
        .map(_.map(_.getName))
    }

  def getBlob(): IO[String] =
    serviceResource.use { s =>
      s.getBlob(containerName, BlobName(defaultBlobName))
        .compile
        .toList
        .map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))
    }

  def downloadBlob(path: Path = Paths.get("/tmp/out.txt")): IO[Unit] =
    serviceResource.use { s =>
      s.downloadBlob(containerName, BlobName(defaultBlobName), path, overwrite = true)
    }

}
