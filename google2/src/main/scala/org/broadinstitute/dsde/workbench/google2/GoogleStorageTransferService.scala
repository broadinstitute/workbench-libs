package org.broadinstitute.dsde.workbench
package google2

import java.nio.file.Path
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Semaphore
import cats.syntax.all._
import com.google.auth.Credentials
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.{Acl, Blob, BlobId, Bucket, StorageOptions}
import com.google.cloud.{Identity, Policy}
import fs2.{Pipe, Stream}
import com.google.cloud.storage.Storage.{BucketGetOption, BucketSourceOption}
import org.typelevel.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates.standardGoogleRetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.language.higherKinds

/**
 * Algebra for Google storage access
 *
 * We follow tagless final pattern similar to https://typelevel.org/cats-tagless/
 */
trait GoogleStorageTransferService[F[_]] {

  def transferBucket(): F[Unit]

}

object GoogleStorageService {
  def resource[F[_]: Async: StructuredLogger](
                                               pathToCredentialJson: String,
                                               blockerBound: Option[Semaphore[F]] = None,
                                               project: Option[GoogleProject] = None
                                             ): Resource[F, GoogleStorageService[F]] =
    for {
      db <- GoogleStorageInterpreter.storage[F](pathToCredentialJson, project)
    } yield GoogleStorageInterpreter[F](db, blockerBound)

  def fromApplicationDefault[F[_]: Async: StructuredLogger](
                                                             blockerBound: Option[Semaphore[F]] = None
                                                           ): Resource[F, GoogleStorageService[F]] =
    for {
      db <- Resource.eval(
        Sync[F].delay(
          StorageOptions
            .newBuilder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .build()
            .getService
        )
      )
    } yield GoogleStorageInterpreter[F](db, blockerBound)

  def fromAccessToken[F[_]: Async: StructuredLogger](
                                                      accessToken: AccessToken,
                                                      blockerBound: Option[Semaphore[F]] = None
                                                    ): Resource[F, GoogleStorageService[F]] =
    for {
      db <- Resource.eval(
        Sync[F].delay(
          StorageOptions
            .newBuilder()
            .setCredentials(GoogleCredentials.create(accessToken))
            .build()
            .getService
        )
      )
    } yield GoogleStorageInterpreter[F](db, blockerBound)
}

final case class GcsBlobName(value: String) extends AnyVal

sealed trait RemoveObjectResult extends Product with Serializable
object RemoveObjectResult {
  def apply(res: Boolean): RemoveObjectResult = if (res) Removed else NotFound

  final case object Removed extends RemoveObjectResult
  final case object NotFound extends RemoveObjectResult
}

sealed abstract class StorageRole extends Product with Serializable {
  def name: String
}
object StorageRole {
  final case object ObjectCreator extends StorageRole {
    def name: String = "roles/storage.objectCreator"
  }
  final case object ObjectViewer extends StorageRole {
    def name: String = "roles/storage.objectViewer"
  }
  final case object ObjectAdmin extends StorageRole {
    def name: String = "roles/storage.objectAdmin"
  }
  final case object StorageAdmin extends StorageRole {
    def name: String = "roles/storage.admin"
  }
  //The custom roleId must be in the form "organizations/{organization_id}/roles/{role}",
  //or "projects/{project_id}/roles/{role}"
  final case class CustomStorageRole(roleId: String) extends StorageRole {
    def name: String = roleId
  }
}

final case class Crc32(asString: String) extends AnyVal
sealed abstract class GetMetadataResponse extends Product with Serializable
object GetMetadataResponse {
  final case object NotFound extends GetMetadataResponse
  final case class Metadata(crc32: Crc32, userDefined: Map[String, String], generation: Long)
    extends GetMetadataResponse
}
