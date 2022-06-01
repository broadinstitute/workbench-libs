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
import com.google.cloud.storage.{Acl, Blob, BlobId, BucketInfo, StorageOptions}
import com.google.cloud.{Identity, Policy, Role}
import fs2.{Pipe, Stream}
import com.google.cloud.storage.Storage.{BucketGetOption, BucketSourceOption}
import org.broadinstitute.dsde.workbench.google2.Implicits.PolicyToStorageRoles
import org.typelevel.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates.standardGoogleRetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.collection.convert.ImplicitConversions._
import scala.language.higherKinds

/**
 * Algebra for Google storage access
 *
 * We follow tagless final pattern similar to https://typelevel.org/cats-tagless/
 */
trait GoogleStorageService[F[_]] {

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def listObjectsWithPrefix(bucketName: GcsBucketName,
                            objectNamePrefix: String,
                            isRecursive: Boolean = false,
                            maxPageSize: Long = 1000,
                            traceId: Option[TraceId] = None,
                            retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, GcsObjectName]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def listBlobsWithPrefix(bucketName: GcsBucketName,
                          objectNamePrefix: String,
                          isRecursive: Boolean,
                          maxPageSize: Long = 1000,
                          traceId: Option[TraceId] = None,
                          retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Blob]

  /**
   * not memory safe. Use listObjectsWithPrefix if you're worried about OOM
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def unsafeListObjectsWithPrefix(
    bucketName: GcsBucketName,
    objectNamePrefix: String,
    maxPageSize: Long = 1000,
    traceId: Option[TraceId] = None,
    retryConfig: RetryConfig = standardGoogleRetryConfig
  )(implicit sf: Sync[F]): F[List[GcsObjectName]] =
    listObjectsWithPrefix(bucketName,
                          objectNamePrefix,
                          maxPageSize = maxPageSize,
                          traceId = traceId,
                          retryConfig = retryConfig
    ).compile.toList

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  @deprecated("Use streamBlob instead", "0.11")
  def createBlob(bucketName: GcsBucketName,
                 objectName: GcsBlobName,
                 objectContents: Array[Byte],
                 objectType: String = "text/plain",
                 metadata: Map[String, String] = Map.empty,
                 generation: Option[Long] = None,
                 traceId: Option[TraceId] = None,
                 retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Blob]

  def streamUploadBlob(bucketName: GcsBucketName,
                       objectName: GcsBlobName,
                       metadata: Map[String, String] = Map.empty,
                       generation: Option[Long] = None,
                       overwrite: Boolean = true,
                       traceId: Option[TraceId] = None
  ): Pipe[F, Byte, Unit]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  @deprecated("Use createBlob instead", "0.5")
  def storeObject(bucketName: GcsBucketName,
                  objectName: GcsBlobName,
                  objectContents: Array[Byte],
                  objectType: String,
                  metadata: Map[String, String] = Map.empty,
                  generation: Option[Long] = None,
                  traceId: Option[TraceId] = None
  ): Stream[F, Unit] =
    createBlob(bucketName, objectName, objectContents, objectType, metadata, generation, traceId).void

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def setBucketLifecycle(bucketName: GcsBucketName,
                         lifecycleRules: List[LifecycleRule],
                         traceId: Option[TraceId] = None,
                         retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Unit]

  /**
   * not memory safe. Use getObject if you're worried about OOM
   * @param traceId uuid for tracing a unique call flow in logging
   */
  @deprecated("Use unsafeGetObjectBody instead", "0.5")
  def unsafeGetObject(bucketName: GcsBucketName,
                      blobName: GcsBlobName,
                      traceId: Option[TraceId] = None,
                      retryConfig: RetryConfig = standardGoogleRetryConfig
  ): F[Option[String]] =
    unsafeGetBlobBody(bucketName, blobName, traceId, retryConfig)

  /**
   * not memory safe. Use getObject if you're worried about OOM
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def unsafeGetBlobBody(bucketName: GcsBucketName,
                        blobName: GcsBlobName,
                        traceId: Option[TraceId] = None,
                        retryConfig: RetryConfig = standardGoogleRetryConfig
  ): F[Option[String]]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  @deprecated("Use getObject instead", "0.5")
  def getObject(bucketName: GcsBucketName,
                blobName: GcsBlobName,
                traceId: Option[TraceId] = None,
                retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Byte] =
    getBlobBody(bucketName, blobName, traceId, retryConfig)

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def getBlobBody(bucketName: GcsBucketName,
                  blobName: GcsBlobName,
                  traceId: Option[TraceId] = None,
                  retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Byte]

  /**
   * return com.google.cloud.storage.Blob, which gives you metadata and user defined metadata etc
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def getBlob(bucketName: GcsBucketName,
              blobName: GcsBlobName,
              credential: Option[Credentials] = None,
              traceId: Option[TraceId] = None,
              retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Blob]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def downloadObject(blobId: BlobId,
                     path: Path,
                     traceId: Option[TraceId] = None,
                     retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Unit]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def getObjectMetadata(bucketName: GcsBucketName,
                        blobName: GcsBlobName,
                        traceId: Option[TraceId] = None,
                        retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, GetMetadataResponse]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def setObjectMetadata(bucketName: GcsBucketName,
                        blobName: GcsBlobName,
                        metadata: Map[String, String],
                        traceId: Option[TraceId],
                        retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Unit]

  /**
   * @return true if deleted; false if not found
   */
  def removeObject(bucketName: GcsBucketName,
                   blobName: GcsBlobName,
                   generation: Option[Long] = None,
                   traceId: Option[TraceId] = None,
                   retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, RemoveObjectResult]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   * Acl is deprecated. Use setIamPolicy if possible
   */
  @deprecated("Deprecated in favor of insertBucket", "0.5")
  def createBucket(billingProject: GoogleProject,
                   bucketName: GcsBucketName,
                   acl: Option[NonEmptyList[Acl]] = None,
                   traceId: Option[TraceId] = None,
                   retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Unit] =
    insertBucket(billingProject, bucketName, acl, Map.empty, traceId)

  def getBucket(googleProject: GoogleProject,
                bucketName: GcsBucketName,
                bucketGetOptions: List[BucketGetOption] = List.empty,
                traceId: Option[TraceId] = None
  ): F[Option[BucketInfo]]

  def setRequesterPays(googleProject: GoogleProject,
                       bucketName: GcsBucketName,
                       requesterPaysEnabled: Boolean,
                       traceId: Option[TraceId] = None,
                       retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Unit]

  /**
   * @param googleProject The name of the Google project to create the bucket in
   * @param traceId uuid for tracing a unique call flow in logging
   * Supports adding bucket labels during creation
   * Acl is deprecated. Use setIamPolicy if possible
   */
  def insertBucket(googleProject: GoogleProject,
                   bucketName: GcsBucketName,
                   acl: Option[NonEmptyList[Acl]] = None,
                   labels: Map[String, String] = Map.empty,
                   traceId: Option[TraceId] = None,
                   bucketPolicyOnlyEnabled: Boolean = false,
                   logBucket: Option[GcsBucketName] = None,
                   retryConfig: RetryConfig = standardGoogleRetryConfig,
                   location: Option[String] = None
  ): Stream[F, Unit]

  /**
   * @param googleProject The name of the Google project to create the bucket in
   * @param traceId uuid for tracing a unique call flow in logging
   * Return {@code true} if bucket was deleted, {@code false} if it was not found
   */
  def deleteBucket(googleProject: GoogleProject,
                   bucketName: GcsBucketName,
                   isRecursive: Boolean = false,
                   bucketSourceOptions: List[BucketSourceOption] = List.empty,
                   traceId: Option[TraceId] = None,
                   retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Boolean]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def setBucketPolicyOnly(bucketName: GcsBucketName,
                          bucketPolicyOnlyEnabled: Boolean,
                          traceId: Option[TraceId] = None,
                          retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Unit]

  def setBucketLabels(bucketName: GcsBucketName,
                      labels: Map[String, String],
                      traceId: Option[TraceId] = None,
                      retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Unit]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def setIamPolicy(bucketName: GcsBucketName,
                   roles: Map[StorageRole, NonEmptyList[Identity]],
                   traceId: Option[TraceId] = None,
                   retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Unit]

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def overrideIamPolicy(bucketName: GcsBucketName,
                        roles: Map[StorageRole, NonEmptyList[Identity]],
                        traceId: Option[TraceId] = None,
                        retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Policy]

  def getIamPolicy(bucketName: GcsBucketName,
                   traceId: Option[TraceId] = None,
                   retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Policy]

  /**
   * Remove the specified roles from the bucket IAM policy
   */
  def removeIamPolicy(bucketName: GcsBucketName,
                      rolesToRemove: Map[StorageRole, NonEmptyList[Identity]],
                      traceId: Option[TraceId] = None,
                      retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[F, Unit] =
    for {
      currentPolicy <- getIamPolicy(bucketName, traceId, retryConfig)
      newRoles = rolesToRemove
        .foldLeft(currentPolicy.toBuilder) { (builder, role) =>
          builder.removeIdentity(Role.of(role._1.name), role._2.head, role._2.tail: _*)
        }
        .build
        .asStorageRoles
      _ <- overrideIamPolicy(bucketName, newRoles, traceId, retryConfig)
    } yield ()
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
  final case object LegacyBucketReader extends StorageRole {
    def name: String = "roles/storage.legacyBucketReader"
  }
  final case object LegacyBucketWriter extends StorageRole {
    def name: String = "roles/storage.legacyBucketWriter"
  }
  final case class CustomStorageRole(roleId: String) extends StorageRole {
    def name: String = roleId
  }

  final def fromString(roleId: String): StorageRole =
    List(
      ObjectCreator,
      ObjectViewer,
      ObjectAdmin,
      StorageAdmin,
      LegacyBucketReader,
      LegacyBucketWriter
    )
      .find(_.name == roleId)
      .getOrElse(CustomStorageRole(roleId))
}

object Implicits {
  implicit class PolicyToStorageRoles(policy: Policy) {
    final def asStorageRoles: Map[StorageRole, NonEmptyList[Identity]] = policy.getBindings
      .foldLeft(Map.newBuilder[StorageRole, NonEmptyList[Identity]]) { (builder, binding) =>
        NonEmptyList
          .fromList(binding._2.toList)
          .map { identities =>
            builder += (StorageRole.fromString(binding._1.getValue) -> identities)
          }
          .getOrElse(builder)
      }
      .result
  }
}

final case class Crc32(asString: String) extends AnyVal
sealed abstract class GetMetadataResponse extends Product with Serializable
object GetMetadataResponse {
  final case object NotFound extends GetMetadataResponse
  final case class Metadata(crc32: Crc32, userDefined: Map[String, String], generation: Long)
      extends GetMetadataResponse
}
