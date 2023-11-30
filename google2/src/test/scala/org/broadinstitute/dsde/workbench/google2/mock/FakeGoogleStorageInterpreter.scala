package org.broadinstitute.dsde.workbench.google2
package mock

import java.nio.file.Path
import cats.data.NonEmptyList
import cats.effect.IO
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.Credentials
import com.google.cloud.storage.Storage.{
  BlobGetOption,
  BlobListOption,
  BlobSourceOption,
  BlobTargetOption,
  BlobWriteOption,
  BucketSourceOption,
  BucketTargetOption
}
import com.google.cloud.storage.{Acl, Blob, BlobId, BucketInfo, Storage, StorageClass}
import com.google.cloud.{Identity, Policy}
import fs2.{Pipe, Stream}
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.GoogleStorageInterpreterSpec._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates.standardGoogleRetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject, IamPermission}
import org.broadinstitute.dsde.workbench.util2.RemoveObjectResult

import java.net.URL
import scala.concurrent.duration.TimeUnit

class BaseFakeGoogleStorage extends GoogleStorageService[IO] {
  override def listObjectsWithPrefix(bucketName: GcsBucketName,
                                     objectNamePrefix: String,
                                     isRecursive: Boolean,
                                     maxPageSize: Long = 1000,
                                     traceId: Option[TraceId] = None,
                                     retryConfig: RetryConfig,
                                     blobListOptions: List[BlobListOption]
  ): fs2.Stream[IO, GcsObjectName] =
    localStorage.listObjectsWithPrefix(bucketName, objectNamePrefix, isRecursive)

  override def listBlobsWithPrefix(bucketName: GcsBucketName,
                                   objectNamePrefix: String,
                                   isRecursive: Boolean,
                                   maxPageSize: Long = 1000,
                                   traceId: Option[TraceId] = None,
                                   retryConfig: RetryConfig,
                                   blobListOptions: List[BlobListOption]
  ): fs2.Stream[IO, Blob] =
    localStorage.listBlobsWithPrefix(bucketName, objectNamePrefix, isRecursive)

  override def unsafeGetBlobBody(bucketName: GcsBucketName,
                                 blobName: GcsBlobName,
                                 traceId: Option[TraceId] = None,
                                 retryConfig: RetryConfig,
                                 blobGetOptions: List[BlobGetOption]
  ): IO[Option[String]] =
    localStorage.unsafeGetBlobBody(bucketName, blobName)

  override def setBucketLifecycle(bucketName: GcsBucketName,
                                  lifecycleRules: List[BucketInfo.LifecycleRule],
                                  traceId: Option[TraceId] = None,
                                  retryConfig: RetryConfig,
                                  bucketTargetOptions: List[BucketTargetOption]
  ): Stream[IO, Unit] = Stream.empty

  override def getBlobBody(bucketName: GcsBucketName,
                           blobName: GcsBlobName,
                           traceId: Option[TraceId] = None,
                           retryConfig: RetryConfig,
                           blobGetOptions: List[BlobGetOption]
  ): Stream[IO, Byte] =
    localStorage.getBlobBody(bucketName, blobName, traceId)

  override def getBlob(bucketName: GcsBucketName,
                       blobName: GcsBlobName,
                       credentials: Option[Credentials] = None,
                       traceId: Option[TraceId] = None,
                       retryConfig: RetryConfig,
                       blobGetOptions: List[BlobGetOption]
  ): Stream[IO, Blob] =
    localStorage.getBlob(bucketName, blobName, credentials, traceId)

  override def getSignedBlobUrl(bucketName: GcsBucketName,
                                blobName: GcsBlobName,
                                signingCredentials: ServiceAccountCredentials,
                                traceId: Option[TraceId] = None,
                                retryConfig: RetryConfig,
                                expirationTime: Long,
                                expirationTimeUnit: TimeUnit,
                                queryParams: Map[String, String]
  ): Stream[IO, URL] =
    localStorage.getSignedBlobUrl(bucketName,
                                  blobName,
                                  signingCredentials,
                                  traceId,
                                  expirationTime = expirationTime,
                                  expirationTimeUnit = expirationTimeUnit,
                                  queryParams = queryParams
    )

  override def downloadObject(blobId: BlobId,
                              path: Path,
                              traceId: Option[TraceId] = None,
                              retryConfig: RetryConfig,
                              blobGetOptions: List[BlobGetOption]
  ): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def getObjectMetadata(bucketName: GcsBucketName,
                                 blobName: GcsBlobName,
                                 traceId: Option[TraceId],
                                 retryConfig: RetryConfig,
                                 blobGetOptions: List[BlobGetOption]
  ): Stream[IO, GetMetadataResponse] =
    localStorage.getObjectMetadata(bucketName, blobName, traceId)

  override def setObjectMetadata(bucketName: GcsBucketName,
                                 objectName: GcsBlobName,
                                 metadata: Map[String, String],
                                 traceId: Option[TraceId],
                                 retryConfig: RetryConfig,
                                 blobTargetOptions: List[BlobTargetOption]
  ): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def removeObject(bucketName: GcsBucketName,
                            blobName: GcsBlobName,
                            generation: Option[Long],
                            traceId: Option[TraceId] = None,
                            retryConfig: RetryConfig,
                            blobSourceOptions: List[BlobSourceOption]
  ): Stream[IO, RemoveObjectResult] =
    localStorage.removeObject(bucketName, blobName).as(RemoveObjectResult.Removed)

  override def insertBucket(googleProject: GoogleProject,
                            bucketName: GcsBucketName,
                            acl: Option[NonEmptyList[Acl]] = None,
                            labels: Map[String, String] = Map.empty,
                            traceId: Option[TraceId] = None,
                            bucketPolicyOnlyEnabled: Boolean = false,
                            logBucket: Option[GcsBucketName] = None,
                            retryConfig: RetryConfig,
                            location: Option[String] = None,
                            bucketTargetOptions: List[BucketTargetOption],
                            autoclassEnabled: Boolean = false,
                            autoclassTerminalStorageClass: Option[StorageClass] = None
  ): Stream[IO, Unit] = Stream.empty

  override def deleteBucket(googleProject: GoogleProject,
                            bucketName: GcsBucketName,
                            isRecursive: Boolean,
                            bucketSourceOptions: List[BucketSourceOption],
                            traceId: Option[TraceId] = None,
                            retryConfig: RetryConfig = standardGoogleRetryConfig
  ): Stream[IO, Boolean] =
    Stream.emit(true).covary[IO]

  override def setBucketPolicyOnly(bucketName: GcsBucketName,
                                   bucketOnlyPolicyEnabled: Boolean,
                                   traceId: Option[TraceId] = None,
                                   retryConfig: RetryConfig,
                                   bucketTargetOptions: List[BucketTargetOption]
  ): Stream[IO, Unit] = Stream.empty

  override def setBucketLabels(bucketName: GcsBucketName,
                               labels: Map[String, String],
                               traceId: Option[TraceId] = None,
                               retryConfig: RetryConfig,
                               bucketTargetOptions: List[BucketTargetOption]
  ): Stream[IO, Unit] = Stream.empty

  override def setIamPolicy(bucketName: GcsBucketName,
                            roles: Map[StorageRole, NonEmptyList[Identity]],
                            traceId: Option[TraceId] = None,
                            retryConfig: RetryConfig,
                            bucketSourceOptions: List[BucketSourceOption]
  ): Stream[IO, Unit] = Stream.empty

  override def overrideIamPolicy(bucketName: GcsBucketName,
                                 roles: Map[StorageRole, NonEmptyList[Identity]],
                                 traceId: Option[TraceId] = None,
                                 retryConfig: RetryConfig,
                                 bucketSourceOptions: List[BucketSourceOption],
                                 version: Int
  ): Stream[IO, Policy] = setIamPolicy(bucketName, roles, traceId, retryConfig) >> getIamPolicy(bucketName, traceId)

  override def createBlob(bucketName: GcsBucketName,
                          objectName: GcsBlobName,
                          objectContents: Array[Byte],
                          objectType: String,
                          metadata: Map[String, String],
                          generation: Option[Long],
                          traceId: Option[TraceId],
                          retryConfig: RetryConfig
  ): Stream[IO, Blob] =
    localStorage.createBlob(bucketName, objectName, objectContents, objectType, metadata, generation, traceId)

  override def getIamPolicy(bucketName: GcsBucketName,
                            traceId: Option[TraceId],
                            retryConfig: RetryConfig,
                            bucketSourceOptions: List[BucketSourceOption]
  ): Stream[IO, Policy] =
    localStorage.getIamPolicy(bucketName, traceId)

  override def streamUploadBlob(bucketName: GcsBucketName,
                                objectName: GcsBlobName,
                                metadata: Map[String, String],
                                generation: Option[Long],
                                overwrite: Boolean,
                                traceId: Option[TraceId],
                                blobWriteOptions: List[BlobWriteOption]
  ): Pipe[IO, Byte, Unit] =
    localStorage.streamUploadBlob(bucketName, objectName, metadata, generation, overwrite, traceId)

  override def getBucket(googleProject: GoogleProject,
                         bucketName: GcsBucketName,
                         bucketGetOptions: List[Storage.BucketGetOption],
                         traceId: Option[TraceId],
                         warnOnError: Boolean = false
  ): IO[Option[BucketInfo]] = IO.pure(None)

  override def setRequesterPays(bucketName: GcsBucketName,
                                requesterPaysEnabled: Boolean,
                                traceId: Option[TraceId] = None,
                                retryConfig: RetryConfig,
                                bucketTargetOptions: List[BucketTargetOption]
  ): Stream[IO, Unit] = Stream.empty

  override def testIamPermissions(bucketName: GcsBucketName,
                                  permissions: List[IamPermission],
                                  traceId: Option[TraceId],
                                  retryConfig: RetryConfig,
                                  bucketSourceOptions: List[BucketSourceOption]
  ): Stream[IO, List[IamPermission]] =
    localStorage.testIamPermissions(bucketName, permissions, traceId, retryConfig, bucketSourceOptions)
}

object FakeGoogleStorageInterpreter extends BaseFakeGoogleStorage
