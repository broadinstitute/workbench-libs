package org.broadinstitute.dsde.workbench.google2
package mock

import java.nio.file.Path

import cats.effect.IO
import com.google.cloud.storage.{Acl, Blob, BlobId, BucketInfo}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}
import GoogleStorageInterpreterSpec._
import cats.data.NonEmptyList
import com.google.cloud.Identity
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId

class BaseFakeGoogleStorage extends GoogleStorageService[IO] {
  override def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000, traceId: Option[TraceId] = None): fs2.Stream[IO, GcsObjectName] = localStorage.listObjectsWithPrefix(bucketName, objectNamePrefix)

  override def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[BucketInfo.LifecycleRule], traceId: Option[TraceId] = None): Stream[IO, Unit] = Stream.empty

  override def unsafeGetBlobBody(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): IO[Option[String]] = localStorage.unsafeGetBlobBody(bucketName, blobName)

  override def getBlobBody(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): Stream[IO, Byte] = localStorage.getBlobBody(bucketName, blobName, traceId)

  override def getBlob(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): Stream[IO, Blob] = localStorage.getBlob(bucketName, blobName, traceId)

  override def downloadObject(blobId: BlobId, path: Path, traceId: Option[TraceId] = None): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def getObjectMetadata(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId]): Stream[IO, GetMetadataResponse] = localStorage.getObjectMetadata(bucketName, blobName, traceId)

  override def setObjectMetadata(bucketName: GcsBucketName, objectName: GcsBlobName, metadata: Map[String, String], traceId: Option[TraceId]): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def removeObject(bucketName: GcsBucketName, blobName: GcsBlobName, generation: Option[Long], traceId: Option[TraceId] = None): Stream[IO, RemoveObjectResult] = localStorage.removeObject(bucketName, blobName).as(RemoveObjectResult.Removed)

  override def insertBucket(bucketName: GcsBucketName, acl: Option[NonEmptyList[Acl]] = None, labels: Map[String, String] = Map.empty, traceId: Option[TraceId] = None): Stream[IO, Unit] = Stream.empty

  override def setBucketPolicyOnly(bucketName: GcsBucketName, bucketOnlyPolicyEnabled: Boolean, traceId: Option[TraceId] = None): Stream[IO, Unit] = Stream.empty

  override def setIamPolicy(bucketName: GcsBucketName, roles: Map[StorageRole, NonEmptyList[Identity]], traceId: Option[TraceId] = None): Stream[IO, Unit] = Stream.empty

  override def createBlob(bucketName: GcsBucketName, objectName: GcsBlobName, objectContents: Array[Byte], objectType: String, metadata: Map[String, String], generation: Option[Long], traceId: Option[TraceId]): Stream[IO, Blob] = localStorage.createBlob(bucketName, objectName, objectContents, objectType, metadata, generation, traceId)
}

object FakeGoogleStorageInterpreter extends BaseFakeGoogleStorage