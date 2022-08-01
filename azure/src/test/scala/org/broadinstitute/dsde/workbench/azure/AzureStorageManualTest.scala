package org.broadinstitute.dsde.workbench.azure

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Path, Paths}
import java.util.UUID

import cats.effect.{IO, Resource}
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._

final class AzureStorageManualTest(connectionAuthString: String, container: String = "testconn") {

  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = Slf4jLogger.getLogger[IO]

  val serviceResource: Resource[IO, AzureStorageService[IO]] =
    AzureStorageService.fromAzureAppRegistrationConfig(AzureStorageConfig(10 minutes))

  def useService(fa: AzureStorageService[IO] => IO[Unit]) =
    serviceResource.use { service =>
      fa(service)
    }

  val connectionString: ConnectionString = ConnectionString(connectionAuthString)
  val containerName = ContainerName(container)

  val defaultBlobName = "testblob"

  def createContainer(): IO[Unit] =
    serviceResource.use { s =>
      s.createContainer(connectionString, containerName)
    }

  def uploadBlob(uploadString: String = "contents", blobName: String = defaultBlobName): IO[Unit] =
    serviceResource.use { s =>
      fs2.Stream
        .emits(uploadString.getBytes(Charset.forName("UTF-8")))
        .covary[IO]
        .through(s.uploadBlob(connectionString, containerName, BlobName(blobName)))
        .compile
        .drain
    }

  def getBlob(): IO[String] =
    serviceResource.use { s =>
      s.getBlob(connectionString, containerName, BlobName(defaultBlobName))
        .compile
        .toList
        .map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))
    }

  def downloadBlob(path: Path = Paths.get("/tmp/out.txt")): IO[Unit] =
    serviceResource.use { s =>
      s.downloadBlob(connectionString, containerName, BlobName(defaultBlobName), path, true)
    }

}
