package org.broadinstitute.dsde.workbench
package google2

import java.nio.channels.Channels
import java.nio.file.{Path, Paths}
import java.time.Instant
import fs2.io.file.Files
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Semaphore
import cats.syntax.all._
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.Storage.{
  BlobGetOption,
  BlobListOption,
  BlobSourceOption,
  BlobTargetOption,
  BlobWriteOption,
  BucketGetOption,
  BucketSourceOption,
  BucketTargetOption,
  SignUrlOption
}
import com.google.cloud.storage.{Acl, Blob, BlobId, BlobInfo, BucketInfo, Storage, StorageOptions}
import com.google.cloud.{Identity, Policy, Role}
import fs2.{text, Pipe, Stream}
import org.typelevel.log4cats.StructuredLogger
import io.circe.Decoder
import io.circe.fs2._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates.standardGoogleRetryConfig
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchException}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject, IamPermission}
import com.google.auth.Credentials
import org.broadinstitute.dsde.workbench.util2.{withLogging, RemoveObjectResult}

import java.net.URL
import java.util.concurrent.TimeUnit
import java.{lang, util}
import scala.jdk.CollectionConverters._

private[google2] class GoogleStorageInterpreter[F[_]](
  db: Storage,
  blockerBound: Option[Semaphore[F]]
)(implicit logger: StructuredLogger[F], F: Async[F])
    extends GoogleStorageService[F] {
  override def listObjectsWithPrefix(bucketName: GcsBucketName,
                                     objectNamePrefix: String,
                                     isRecursive: Boolean,
                                     maxPageSize: Long,
                                     traceId: Option[TraceId],
                                     retryConfig: RetryConfig,
                                     blobListOptions: List[BlobListOption]
  ): Stream[F, GcsObjectName] =
    listBlobsWithPrefix(bucketName, objectNamePrefix, isRecursive, maxPageSize, traceId, retryConfig, blobListOptions)
      .map(blob => GcsObjectName(blob.getName, Instant.ofEpochMilli(blob.getCreateTime)))

  override def listBlobsWithPrefix(bucketName: GcsBucketName,
                                   objectNamePrefix: String,
                                   isRecursive: Boolean,
                                   maxPageSize: Long,
                                   traceId: Option[TraceId],
                                   retryConfig: RetryConfig,
                                   blobListOptions: List[BlobListOption]
  ): Stream[F, Blob] = {
    val extraBlobListOptions =
      if (isRecursive)
        List(BlobListOption.prefix(objectNamePrefix), BlobListOption.pageSize(maxPageSize.longValue()))
      else
        List(BlobListOption.prefix(objectNamePrefix),
             BlobListOption.pageSize(maxPageSize.longValue()),
             BlobListOption.currentDirectory()
        )

    val result = for {
      blob <- listBlobs(db, bucketName, blobListOptions ++ extraBlobListOptions, traceId, retryConfig, true)
    } yield
    // Remove directory from end result
    // For example, if you have `bucketName/dir1/object1` in GCS, remove `bucketName/dir1/` from the end result
    if (blob.getName.endsWith("/"))
      None
    else
      Option(blob)
    result.unNone
  }

  override def unsafeGetBlobBody(bucketName: GcsBucketName,
                                 blobName: GcsBlobName,
                                 traceId: Option[TraceId],
                                 retryConfig: RetryConfig,
                                 blobGetOptions: List[BlobGetOption]
  ): F[Option[String]] =
    getBlobBody(bucketName, blobName, traceId, retryConfig, blobGetOptions)
      .through(text.utf8Decode)
      .compile
      .foldSemigroup

  override def getBlobBody(bucketName: GcsBucketName,
                           blobName: GcsBlobName,
                           traceId: Option[TraceId],
                           retryConfig: RetryConfig,
                           blobGetOptions: List[BlobGetOption]
  ): Stream[F, Byte] = {
    val getBlobs =
      blockingF(F.delay(db.get(BlobId.of(bucketName.value, blobName.value), blobGetOptions: _*))).map(Option(_))

    for {
      blobOpt <- retryF(retryConfig)(
        getBlobs,
        traceId,
        s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)}, $blobGetOptions)"
      )
      r <- blobOpt match {
        case Some(blob) =>
          // implementation based on fs-blobstore
          fs2.io.readInputStream(
            F.delay(
              Channels
                .newInputStream {
                  val reader = blob.reader()
                  reader.setChunkSize(chunkSize)
                  reader
                }
            ),
            chunkSize,
            closeAfterUse = true
          )
        case None => Stream.empty
      }
    } yield r
  }

  override def getBlob(bucketName: GcsBucketName,
                       blobName: GcsBlobName,
                       credentials: Option[Credentials],
                       traceId: Option[TraceId],
                       retryConfig: RetryConfig,
                       blobGetOptions: List[BlobGetOption]
  ): Stream[F, Blob] = {
    val dbForCredential = credentials match {
      case Some(c) => db.getOptions.toBuilder.setCredentials(c).build().getService
      case None    => db
    }

    val getBlobs =
      blockingF(Async[F].delay(dbForCredential.get(BlobId.of(bucketName.value, blobName.value), blobGetOptions: _*)))
        .map(Option(_))

    retryF(retryConfig)(
      getBlobs,
      traceId,
      s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)}, $blobGetOptions)"
    ).unNone
  }

  override def getSignedBlobUrl(bucketName: GcsBucketName,
                                blobName: GcsBlobName,
                                signingCredentials: ServiceAccountCredentials,
                                traceId: Option[TraceId],
                                retryConfig: RetryConfig,
                                expirationTime: Long,
                                expirationTimeUnit: TimeUnit,
                                queryParams: Map[String, String]
  ): Stream[F, URL] = {
    val dbForCredential = db.getOptions.toBuilder.setCredentials(signingCredentials).build().getService
    val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName.value, blobName.value)).build
    val signBlob =
      blockingF(
        Async[F].delay(
          dbForCredential.signUrl(
            blobInfo,
            expirationTime,
            expirationTimeUnit,
            SignUrlOption.signWith(signingCredentials),
            SignUrlOption.withQueryParams(queryParams.asJava),
            SignUrlOption.withV4Signature()
          )
        )
      )
        .map(Option(_))

    retryF(retryConfig)(
      signBlob,
      traceId,
      s"com.google.cloud.storage.Storage.signUrl(${BlobId.of(bucketName.value, blobName.value)}, ${expirationTime}, ${expirationTimeUnit
          .name()}, SignUrlOption.signWith(${signingCredentials.getClientEmail}))"
    ).unNone
  }

  def downloadObject(blobId: BlobId,
                     path: Path,
                     traceId: Option[TraceId],
                     retryConfig: RetryConfig,
                     blobGetOptions: List[BlobGetOption]
  ): Stream[F, Unit] = {
    val downLoad = blockingF(Async[F].delay(db.get(blobId, blobGetOptions: _*).downloadTo(path)))

    retryF(retryConfig)(downLoad, traceId, s"com.google.cloud.storage.Storage.get($blobId, $blobGetOptions).download")
  }

  override def getObjectMetadata(bucketName: GcsBucketName,
                                 blobName: GcsBlobName,
                                 traceId: Option[TraceId],
                                 retryConfig: RetryConfig,
                                 blobGetOptions: List[BlobGetOption]
  ): Stream[F, GetMetadataResponse] = {
    val getBlobs =
      blockingF(Async[F].delay(db.get(BlobId.of(bucketName.value, blobName.value), blobGetOptions: _*))).map(Option(_))

    for {
      blobOpt <- retryF(retryConfig)(
        getBlobs,
        traceId,
        s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)}, $blobGetOptions)"
      )
      r = blobOpt match {
        case Some(blob) =>
          Option(blob.getMetadata) match {
            case None    => GetMetadataResponse.Metadata(Crc32(blob.getCrc32c), Map.empty, blob.getGeneration)
            case Some(x) => GetMetadataResponse.Metadata(Crc32(blob.getCrc32c), x.asScala.toMap, blob.getGeneration)
          }
        case None => GetMetadataResponse.NotFound
      }
    } yield r
  }

  // Overwrites all of the metadata on the GCS object with the provided metadata
  override def setObjectMetadata(bucketName: GcsBucketName,
                                 objectName: GcsBlobName,
                                 metadata: Map[String, String],
                                 traceId: Option[TraceId],
                                 retryConfig: RetryConfig,
                                 blobTargetOptions: List[BlobTargetOption]
  ): Stream[F, Unit] = {
    val blobId = BlobId.of(bucketName.value, objectName.value)
    val blobInfo = BlobInfo
      .newBuilder(blobId)
      .setMetadata(metadata.asJava)
      .build()

    val metadataUpdate = blockingF(Async[F].delay(db.update(blobInfo, blobTargetOptions: _*)))

    retryF(retryConfig)(metadataUpdate,
                        traceId,
                        s"com.google.cloud.storage.Storage.update($bucketName/${objectName.value}, $blobTargetOptions)"
    ).void
  }

  override def streamUploadBlob(bucketName: GcsBucketName,
                                objectName: GcsBlobName,
                                metadata: Map[String, String],
                                generation: Option[Long],
                                overwrite: Boolean,
                                traceId: Option[TraceId],
                                blobWriteOptions: List[BlobWriteOption]
  ): Pipe[F, Byte, Unit] = {
    val (blobInfo, generationOption) = generation match {
      case Some(g) =>
        val blobId = BlobId.of(bucketName.value, objectName.value, g)
        (BlobInfo
           .newBuilder(blobId)
           .setMetadata(metadata.asJava)
           .build(),
         List(BlobWriteOption.generationMatch())
        )
      case None =>
        val blobId = BlobId.of(bucketName.value, objectName.value)
        (BlobInfo
           .newBuilder(blobId)
           .setMetadata(metadata.asJava)
           .build(),
         List.empty[BlobWriteOption]
        )
    }

    val outputStream = F.delay {
      val options =
        if (overwrite) generationOption
        else List(BlobWriteOption.doesNotExist()) ++ generationOption ++ blobWriteOptions
      val writer = db.writer(blobInfo, options: _*)
      Channels.newOutputStream(writer)
    }
    fs2.io.writeOutputStream(outputStream, closeAfterUse = true)
  }

  override def createBlob(bucketName: GcsBucketName,
                          objectName: GcsBlobName,
                          objectContents: Array[Byte],
                          objectType: String,
                          metadata: Map[String, String],
                          generation: Option[Long],
                          traceId: Option[TraceId],
                          retryConfig: RetryConfig
  ): Stream[F, Blob] = {
    val storeObject: F[Blob] = generation match {
      case Some(g) =>
        val blobId = BlobId.of(bucketName.value, objectName.value, g)
        val blobInfo = BlobInfo
          .newBuilder(blobId)
          .setContentType(objectType)
          .setMetadata(metadata.asJava)
          .build()
        blockingF(Async[F].delay(db.create(blobInfo, objectContents, BlobTargetOption.generationMatch())))
      case None =>
        val blobId = BlobId.of(bucketName.value, objectName.value)
        val blobInfo = BlobInfo
          .newBuilder(blobId)
          .setContentType(objectType)
          .setMetadata(metadata.asJava)
          .build()
        blockingF(Async[F].delay(db.create(blobInfo, objectContents)))
    }

    retryF(retryConfig)(storeObject,
                        traceId,
                        s"com.google.cloud.storage.Storage.create($bucketName/${objectName.value}, xxx)"
    )
  }

  override def removeObject(bucketName: GcsBucketName,
                            blobName: GcsBlobName,
                            generation: Option[Long],
                            traceId: Option[TraceId],
                            retryConfig: RetryConfig,
                            blobSourceOptions: List[BlobSourceOption]
  ): Stream[F, RemoveObjectResult] = {
    val deleteObject: F[Boolean] = generation match {
      case Some(g) =>
        val blobId = BlobId.of(bucketName.value, blobName.value, g)
        blockingF(Async[F].delay(db.delete(blobId, blobSourceOptions :+ BlobSourceOption.generationMatch(g): _*)))
      case None =>
        blockingF(Async[F].delay(db.delete(BlobId.of(bucketName.value, blobName.value), blobSourceOptions: _*)))
    }

    for {
      deleted <- retryF(retryConfig)(
        deleteObject,
        traceId,
        s"com.google.cloud.storage.Storage.delete(${bucketName.value}/${blobName.value}, $blobSourceOptions)"
      )
    } yield RemoveObjectResult(deleted)
  }

  override def insertBucket(googleProject: GoogleProject,
                            bucketName: GcsBucketName,
                            acl: Option[NonEmptyList[Acl]],
                            labels: Map[String, String],
                            traceId: Option[TraceId],
                            bucketPolicyOnlyEnabled: Boolean,
                            logBucket: Option[GcsBucketName],
                            retryConfig: RetryConfig,
                            location: Option[String],
                            bucketTargetOptions: List[BucketTargetOption],
                            autoclassEnabled: Boolean
  ): Stream[F, Unit] = {

    if (acl.isDefined && bucketPolicyOnlyEnabled) {
      throw new WorkbenchException(
        "Cannot set acls on a bucket that has bucketPolicyOnlyEnabled set to true. Either set uniform bucket-level access OR provide a list of acls."
      )
    }

    val iamConfig =
      BucketInfo.IamConfiguration.newBuilder().setIsUniformBucketLevelAccessEnabled(bucketPolicyOnlyEnabled).build()

    val bucketInfoBuilder = BucketInfo
      .of(bucketName.value)
      .toBuilder
      .setLabels(labels.asJava)
      .setIamConfiguration(iamConfig)
      .setAutoclass(BucketInfo.Autoclass.newBuilder().setEnabled(autoclassEnabled).build())

    logBucket.map { logBucketName =>
      val logging = BucketInfo.Logging.newBuilder().setLogBucket(logBucketName.value).build()
      bucketInfoBuilder.setLogging(logging)
    }

    // set the location if passed, else the bucket will default to `us multi-region`
    location.foreach(bucketInfoBuilder.setLocation)

    val bucketInfo = acl
      .map { aclList =>
        val acls = aclList.toList.asJava
        bucketInfoBuilder
          .setAcl(acls)
          .setDefaultAcl(acls)
          .build()
      }
      .getOrElse(bucketInfoBuilder.build())

    val dbForProject = db.getOptions.toBuilder.setProjectId(googleProject.value).build().getService

    val createBucket =
      blockingF(Async[F].delay(dbForProject.create(bucketInfo, bucketTargetOptions: _*))).void.recoverWith {
        case e: com.google.cloud.storage.StorageException if e.getCode == 409 =>
          StructuredLogger[F].info(s"$bucketName already exists")
      }

    retryF(retryConfig)(
      createBucket,
      traceId,
      s"com.google.cloud.storage.Storage.create($bucketInfo, ${googleProject.value}, $bucketTargetOptions)"
    )
  }

  override def deleteBucket(googleProject: GoogleProject,
                            bucketName: GcsBucketName,
                            isRecursive: Boolean,
                            bucketSourceOptions: List[BucketSourceOption],
                            traceId: Option[TraceId],
                            retryConfig: RetryConfig
  ): Stream[F, Boolean] = {
    val dbForProject = db.getOptions.toBuilder.setProjectId(googleProject.value).build().getService

    val allBlobs = listBlobs(dbForProject, bucketName, List.empty, traceId, retryConfig, false).map(_.getBlobId)

    for {
      _ <-
        if (isRecursive) {
          val r = for {
            allBlobs <- allBlobs.compile.toList
            isSuccess <-
              if (allBlobs.isEmpty)
                Async[F].pure(true)
              else Async[F].delay(dbForProject.delete(allBlobs.asJava).asScala.toList.forall(x => x))
            _ <-
              if (isSuccess)
                logger.info(s"Successfully deleted all objects in ${bucketName.value}")
              else
                Async[F].raiseError(new RuntimeException(s"failed to delete all objects in ${bucketName.value}"))
          } yield ()
          Stream.eval(r)
        } else Stream.eval(Async[F].unit)
      deleteBucket = Async[F].delay(dbForProject.delete(bucketName.value, bucketSourceOptions: _*))
      res <- retryF(retryConfig)(
        deleteBucket,
        traceId,
        s"com.google.cloud.storage.Storage.delete(${bucketName.value}, $bucketSourceOptions)"
      )
    } yield res
  }

  override def getBucket(googleProject: GoogleProject,
                         bucketName: GcsBucketName,
                         bucketGetOptions: List[BucketGetOption],
                         traceId: Option[TraceId]
  ): F[Option[BucketInfo]] = {
    val dbForProject = db.getOptions.toBuilder.setProjectId(googleProject.value).build().getService
    val fa: F[Option[BucketInfo]] =
      Async[F].delay(dbForProject.get(bucketName.value, bucketGetOptions: _*)).map(Option(_))
    withLogging(fa, traceId, s"com.google.cloud.storage.Storage.get(${bucketName.value}, $bucketGetOptions)")
  }

  override def setRequesterPays(bucketName: GcsBucketName,
                                requesterPaysEnabled: Boolean,
                                traceId: Option[TraceId],
                                retryConfig: RetryConfig,
                                bucketTargetOptions: List[BucketTargetOption]
  ): Stream[F, Unit] = {
    val updateBucket = blockingF(
      Async[F].delay(
        db.update(BucketInfo.newBuilder(bucketName.value).setRequesterPays(requesterPaysEnabled).build(),
                  bucketTargetOptions: _*
        )
      )
    )

    retryF(retryConfig)(
      updateBucket,
      traceId,
      s"com.google.cloud.storage.Storage.update($bucketName, requesterPays=$requesterPaysEnabled, $bucketTargetOptions)"
    ).void
  }

  override def setBucketPolicyOnly(bucketName: GcsBucketName,
                                   bucketPolicyOnlyEnabled: Boolean,
                                   traceId: Option[TraceId],
                                   retryConfig: RetryConfig,
                                   bucketTargetOptions: List[BucketTargetOption]
  ): Stream[F, Unit] = {
    val iamConfiguration =
      BucketInfo.IamConfiguration.newBuilder().setIsUniformBucketLevelAccessEnabled(bucketPolicyOnlyEnabled).build()
    val updateBucket = blockingF(
      Async[F].delay(
        db.update(BucketInfo.newBuilder(bucketName.value).setIamConfiguration(iamConfiguration).build(),
                  bucketTargetOptions: _*
        )
      )
    )

    retryF(retryConfig)(
      updateBucket,
      traceId,
      s"com.google.cloud.storage.Storage.update($bucketName, $bucketTargetOptions)"
    ).void
  }

  override def setBucketLabels(bucketName: GcsBucketName,
                               labels: Map[String, String],
                               traceId: Option[TraceId],
                               retryConfig: RetryConfig,
                               bucketTargetOptions: List[BucketTargetOption]
  ): Stream[F, Unit] = {
    val updateBucket = blockingF(
      Async[F].delay(
        db.update(BucketInfo.newBuilder(bucketName.value).setLabels(labels.asJava).build(), bucketTargetOptions: _*)
      )
    )

    retryF(retryConfig)(
      updateBucket,
      traceId,
      s"com.google.cloud.storage.Storage.update($bucketName, $labels, $bucketTargetOptions)"
    ).void
  }

  override def setIamPolicy(bucketName: GcsBucketName,
                            roles: Map[StorageRole, NonEmptyList[Identity]],
                            traceId: Option[TraceId],
                            retryConfig: RetryConfig,
                            bucketSourceOptions: List[BucketSourceOption]
  ): Stream[F, Unit] = {
    val getAndSetIamPolicy = for {
      policy <- blockingF(Async[F].delay(db.getIamPolicy(bucketName.value, bucketSourceOptions: _*)))
      policyBuilder = policy.toBuilder()
      updatedPolicy = roles
        .foldLeft(policyBuilder)((currentBuilder, item) =>
          currentBuilder.addIdentity(Role.of(item._1.name), item._2.head, item._2.tail: _*)
        )
        .build()
      _ <- blockingF(Async[F].delay(db.setIamPolicy(bucketName.value, updatedPolicy, bucketSourceOptions: _*)))
    } yield ()

    retryF(retryConfig)(
      getAndSetIamPolicy,
      traceId,
      s"com.google.cloud.storage.Storage.getIamPolicy($bucketName), com.google.cloud.storage.Storage.setIamPolicy($bucketName, $roles, $bucketSourceOptions)"
    )
  }

  override def overrideIamPolicy(bucketName: GcsBucketName,
                                 roles: Map[StorageRole, NonEmptyList[Identity]],
                                 traceId: Option[TraceId],
                                 retryConfig: RetryConfig,
                                 bucketSourceOptions: List[BucketSourceOption],
                                 version: Int
  ): Stream[F, Policy] = {

    val policyBuilder = Policy.newBuilder()
    val overrideIamPolicy = roles
      .foldLeft(policyBuilder)((currentBuilder, item) =>
        currentBuilder.addIdentity(Role.of(item._1.name), item._2.head, item._2.tail: _*)
      )
      .setVersion(version)
      .build()

    val overrideIam = blockingF(
      Async[F].delay(db.setIamPolicy(bucketName.value, overrideIamPolicy, bucketSourceOptions: _*))
    )

    retryF(retryConfig)(
      overrideIam,
      traceId,
      s"com.google.cloud.storage.Storage.setIamPolicy($bucketName, $roles, $bucketSourceOptions)"
    )
  }

  override def getIamPolicy(bucketName: GcsBucketName,
                            traceId: Option[TraceId],
                            retryConfig: RetryConfig,
                            bucketSourceOptions: List[BucketSourceOption]
  ): Stream[F, Policy] = {
    val getIamPolicy = for {
      policy <- blockingF(Async[F].delay(db.getIamPolicy(bucketName.value, bucketSourceOptions: _*)))
    } yield policy

    retryF(retryConfig)(
      getIamPolicy,
      traceId,
      s"com.google.cloud.storage.Storage.getIamPolicy($bucketName, $bucketSourceOptions)"
    )
  }

  override def testIamPermissions(bucketName: GcsBucketName,
                                  permissions: List[IamPermission],
                                  traceId: Option[TraceId] = None,
                                  retryConfig: RetryConfig = standardGoogleRetryConfig,
                                  bucketSourceOptions: List[BucketSourceOption] = List.empty
  ): Stream[F, List[IamPermission]] =
    retryF(retryConfig)(
      blockingF(
        Async[F].delay(
          db.testIamPermissions(bucketName.value, permissions.map(_.value).asJava, bucketSourceOptions: _*)
        )
      ),
      traceId,
      s"com.google.cloud.storage.Storage.testIamPermissions($bucketName, ${permissions.mkString(",")})"
    ).map { results =>
      GoogleStorageInterpreter.mapTestPermissionResultsToIamPermissions(results, permissions)
    }

  override def setBucketLifecycle(bucketName: GcsBucketName,
                                  lifecycleRules: List[LifecycleRule],
                                  traceId: Option[TraceId],
                                  retryConfig: RetryConfig,
                                  bucketTargetOptions: List[BucketTargetOption]
  ): Stream[F, Unit] = {
    val bucketInfo = BucketInfo
      .of(bucketName.value)
      .toBuilder
      .setLifecycleRules(lifecycleRules.asJava)
      .build()
    retryF(retryConfig)(
      blockingF(Async[F].delay(db.update(bucketInfo, bucketTargetOptions: _*))),
      traceId,
      s"com.google.cloud.storage.Storage.update($bucketInfo, $bucketTargetOptions)"
    ).void
  }

  private def listBlobs(db: Storage,
                        bucketName: GcsBucketName,
                        blobListOptions: List[BlobListOption],
                        traceId: Option[TraceId],
                        retryConfig: RetryConfig,
                        ifErrorWhenBucketNotFound: Boolean
  ): Stream[F, Blob] = {
    val listFirstPage =
      Async[F].delay(db.list(bucketName.value, blobListOptions: _*)).map(p => Option(p)).handleErrorWith {
        case e: com.google.cloud.storage.StorageException if e.getCode == 404 =>
          // This can happen if the bucket doesn't exist
          if (ifErrorWhenBucketNotFound)
            Async[F].raiseError(e)
          else
            Async[F].pure(None)
      }

    for {
      firstPage <- retryF(retryConfig)(
        blockingF(listFirstPage),
        traceId,
        s"com.google.cloud.storage.Storage.list($bucketName, $blobListOptions)"
      ).unNone
      page <- Stream.unfoldEval(firstPage) { currentPage =>
        Option(currentPage).traverse { p =>
          val fetchNext = retryF(retryConfig)(
            blockingF(Async[F].delay(p.getNextPage)),
            traceId,
            s"com.google.api.gax.paging.Page.getNextPage"
          )
          fetchNext.compile.lastOrError.map(next => (p, next))
        }
      }
      blob <- Stream.fromIterator[F](page.getValues.iterator().asScala, 1024).map(b => Option(b)).unNone
    } yield blob
  }

  private def blockingF[A](fa: F[A]): F[A] = blockerBound match {
    case None    => fa
    case Some(s) => s.permit.use(_ => fa)
  }

  private val chunkSize = 1024 * 1024 * 2 // com.google.cloud.storage.BlobReadChannel.DEFAULT_CHUNK_SIZE
}

object GoogleStorageInterpreter {
  def apply[F[_]: Async: StructuredLogger](
    db: Storage,
    blockerBound: Option[Semaphore[F]]
  ): GoogleStorageInterpreter[F] =
    new GoogleStorageInterpreter(db, blockerBound)

  def storage[F[_]: Sync: Files](
    pathToJson: String,
    project: Option[GoogleProject] =
      None // legacy credential file doesn't have `project_id` field. Hence we need to pass in explicitly
  ): Resource[F, Storage] =
    for {
      credential <- org.broadinstitute.dsde.workbench.util2.readFile(pathToJson)
      project <- project match { // Use explicitly passed in project if it's defined; else use `project_id` in json credential; if neither has project defined, raise error
        case Some(p) => Resource.pure[F, GoogleProject](p)
        case None    => Resource.eval(parseProject(pathToJson).compile.lastOrError)
      }
      db <- Resource.eval(
        Sync[F].delay(
          StorageOptions
            .newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(credential))
            .setProjectId(project.value)
            .build()
            .getService
        )
      )
    } yield db

  def mapTestPermissionResultsToIamPermissions(results: util.List[lang.Boolean],
                                               permissions: List[IamPermission]
  ): List[IamPermission] =
    results.asScala
      .map(_.booleanValue())
      .zipWithIndex
      .flatMap {
        case (true, index) => permissions.get(index)
        case (false, _)    => None
      }
      .toList

  implicit val googleProjectDecoder: Decoder[GoogleProject] = Decoder.forProduct1(
    "project_id"
  )(GoogleProject.apply)

  def parseProject[F[_]: Files: Sync](pathToJson: String): Stream[F, GoogleProject] =
    Files[F]
      .readAll(Paths.get(pathToJson), 4096)
      .through(byteStreamParser)
      .through(decoder[F, GoogleProject])
}
