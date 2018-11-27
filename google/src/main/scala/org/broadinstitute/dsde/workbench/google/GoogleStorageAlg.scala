package org.broadinstitute.dsde.workbench.google

import com.google.cloud.storage.Acl
import com.google.cloud.storage.BucketInfo.LifecycleRule
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.language.higherKinds

trait GoogleStorageAlg[F[_]] {
  def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000): Stream[F, GcsObjectName]

  /**
    * not memory safe. Use listObjectsWithPrefix if you're worried about OOM
    */
  def unsafeListObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000): F[List[GcsObjectName]]

  def storeObject(bucketName: GcsBucketName, objectName: GcsBlobName, objectContents: Array[Byte], objectType: String): F[Unit]

  def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[LifecycleRule]): F[Unit]

  /**
    * not memory safe
    */
  def unsafeGetObject(bucketName: GcsBucketName, blobName: GcsBlobName): F[Option[String]]

  def removeObject(bucketName: GcsBucketName, objectName: GcsBlobName): F[Boolean]

  def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, acl: List[Acl]): F[Unit]
}

final case class GcsBlobName(value: String) extends AnyVal
