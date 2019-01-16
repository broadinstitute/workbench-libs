package org.broadinstitute.dsde.workbench.google2

import cats.effect.Sync
import com.google.cloud.storage.Acl
import com.google.cloud.storage.BucketInfo.LifecycleRule
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.language.higherKinds

/**
  * Algebra for Google storage access
  *
  * We follow tagless final pattern similar to https://typelevel.org/cats-tagless/
  */
trait GoogleStorageService[F[_]] {
  def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000): Stream[F, GcsObjectName]

  /**
    * not memory safe. Use listObjectsWithPrefix if you're worried about OOM
    */
  def unsafeListObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000)(implicit sf: Sync[F]): F[List[GcsObjectName]] = listObjectsWithPrefix(bucketName, objectNamePrefix).compile.toList

  def storeObject(bucketName: GcsBucketName, objectName: GcsBlobName, objectContents: Array[Byte], objectType: String): F[Unit]

  def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[LifecycleRule]): F[Unit]

  /**
    * not memory safe
    */
  def unsafeGetObject(bucketName: GcsBucketName, blobName: GcsBlobName): F[Option[String]]

  /**
    * @return true if deleted; false if not found
    */
  def removeObject(bucketName: GcsBucketName, objectName: GcsBlobName): F[RemoveObjectResult]

  def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, acl: List[Acl]): F[Unit]
}

final case class GcsBlobName(value: String) extends AnyVal

sealed trait RemoveObjectResult
object RemoveObjectResult {
  def apply(res: Boolean): RemoveObjectResult = if(res) Removed else NotFound

  final case object Removed extends RemoveObjectResult
  final case object NotFound extends RemoveObjectResult
}
