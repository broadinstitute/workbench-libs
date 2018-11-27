package org.broadinstitute.dsde.workbench.google2
package mock

import cats.implicits._
import cats.effect.IO
import com.google.cloud.storage.{Acl, BucketInfo}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}
import GoogleStorageInterpreterSpec._

object FakeGoogleStorageInterpreter extends GoogleStorageService[IO] {
  override def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000): fs2.Stream[IO, GcsObjectName] = localStorage.listObjectsWithPrefix(bucketName, objectNamePrefix)

  override def storeObject(bucketName: GcsBucketName, objectName: GcsBlobName, objectContents: Array[Byte], objectType: String): IO[Unit] = localStorage.storeObject(bucketName, objectName, objectContents, objectType)

  override def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[BucketInfo.LifecycleRule]): IO[Unit] = IO.unit

  override def unsafeGetObject(bucketName: GcsBucketName, blobName: GcsBlobName): IO[Option[String]] = localStorage.unsafeGetObject(bucketName, blobName)

  override def removeObject(bucketName: GcsBucketName, blobName: GcsBlobName): IO[RemoveObjectResult] = localStorage.removeObject(bucketName, blobName).as(RemoveObjectResult.Removed)

  override def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, acl: List[Acl]): IO[Unit] = IO.unit //this is fine for now since we don't have a getBucket method yet
}
