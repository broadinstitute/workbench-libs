package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import com.google.cloud.Identity
import com.google.cloud.storage.Acl
import com.google.cloud.storage.BucketInfo.LifecycleRule
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.GoogleStorageInterpreter.defaultRetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.concurrent.ExecutionContext
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
  def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000, traceId: Option[TraceId] = None): Stream[F, GcsObjectName]

  /**
    * not memory safe. Use listObjectsWithPrefix if you're worried about OOM
    * @param traceId uuid for tracing a unique call flow in logging
    */
  def unsafeListObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000, traceId: Option[TraceId] = None)(implicit sf: Sync[F]): F[List[GcsObjectName]] = listObjectsWithPrefix(bucketName, objectNamePrefix).compile.toList

  /**
    * @param traceId uuid for tracing a unique call flow in logging
    */
  def storeObject(bucketName: GcsBucketName, objectName: GcsBlobName, objectContents: Array[Byte], objectType: String, traceId: Option[TraceId] = None): F[Unit]

  /**
    * @param traceId uuid for tracing a unique call flow in logging
    */
  def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[LifecycleRule], traceId: Option[TraceId] = None): Stream[F, Unit]

  /**
    * not memory safe. Use getObject if you're worried about OOM
    * @param traceId uuid for tracing a unique call flow in logging
    */
  def unsafeGetObject(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): F[Option[String]]

  /**
    * @param traceId uuid for tracing a unique call flow in logging
    */
  def getObject(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): Stream[F, Byte]

  /**
    * @return true if deleted; false if not found
    */
  def removeObject(bucketName: GcsBucketName, objectName: GcsBlobName, traceId: Option[TraceId] = None): F[RemoveObjectResult]

  /**
    * @param traceId uuid for tracing a unique call flow in logging
    */
  def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, acl: List[Acl], traceId: Option[TraceId] = None): F[Unit]

  /**
    * @param traceId uuid for tracing a unique call flow in logging
    */
  def createBucketWithAdminRole(googleProject: GoogleProject, bucketName: GcsBucketName, adminIdentity: Identity, traceId: Option[TraceId] = None): Stream[F, Unit]
}

object GoogleStorageService {
  def resource[F[_]: ContextShift: Timer: Async: Logger](pathToCredentialJson: String, blockingEc: ExecutionContext, retryConfig: RetryConfig = defaultRetryConfig): Resource[F, GoogleStorageService[F]] = for {
    db <- GoogleStorageInterpreter.storage[F](pathToCredentialJson)
  } yield GoogleStorageInterpreter[F](db, blockingEc, retryConfig)
}

final case class GcsBlobName(value: String) extends AnyVal

sealed trait RemoveObjectResult
object RemoveObjectResult {
  def apply(res: Boolean): RemoveObjectResult = if(res) Removed else NotFound

  final case object Removed extends RemoveObjectResult
  final case object NotFound extends RemoveObjectResult
}
