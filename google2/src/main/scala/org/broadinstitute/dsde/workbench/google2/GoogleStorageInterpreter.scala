package org.broadinstitute.dsde.workbench
package google2

import java.nio.channels.Channels
import java.nio.file.{Path, Paths}
import java.time.Instant

import cats.data.NonEmptyList
import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.Storage.{BlobListOption, BlobSourceOption, BlobTargetOption, BucketSourceOption}
import com.google.cloud.storage.{Acl, Blob, BlobId, BlobInfo, BucketInfo, Storage, StorageOptions}
import com.google.cloud.{Identity, Policy, Role}
import fs2.{text, Stream}
import io.chrisdavenport.log4cats.StructuredLogger
import io.circe.Decoder
import io.circe.fs2._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates.standardRetryConfig
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchException}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}
import com.google.auth.Credentials

import scala.collection.JavaConverters._

private[google2] class GoogleStorageInterpreter[F[_]: ContextShift: Timer: Async](
  db: Storage,
  blocker: Blocker,
  blockerBound: Option[Semaphore[F]]
)(implicit logger: StructuredLogger[F])
    extends GoogleStorageService[F] {
  override def listObjectsWithPrefix(bucketName: GcsBucketName,
                                     objectNamePrefix: String,
                                     isRecursive: Boolean = false,
                                     maxPageSize: Long = 1000,
                                     traceId: Option[TraceId] = None,
                                     retryConfig: RetryConfig): Stream[F, GcsObjectName] =
    listBlobsWithPrefix(bucketName, objectNamePrefix, isRecursive, maxPageSize, traceId, retryConfig).map(
      blob => GcsObjectName(blob.getName, Instant.ofEpochMilli(blob.getCreateTime))
    )

  override def listBlobsWithPrefix(bucketName: GcsBucketName,
                                   objectNamePrefix: String,
                                   isRecursive: Boolean,
                                   maxPageSize: Long = 1000,
                                   traceId: Option[TraceId] = None,
                                   retryConfig: RetryConfig): Stream[F, Blob] = {
    val blobListOptions =
      if (isRecursive)
        List(BlobListOption.prefix(objectNamePrefix), BlobListOption.pageSize(maxPageSize.longValue()))
      else
        List(BlobListOption.prefix(objectNamePrefix),
             BlobListOption.pageSize(maxPageSize.longValue()),
             BlobListOption.currentDirectory())

    val result = for {
      blob <- listBlobs(db, bucketName, blobListOptions, traceId, retryConfig, true)
    } yield {
      // Remove directory from end result
      // For example, if you have `bucketName/dir1/object1` in GCS, remove `bucketName/dir1/` from the end result
      if (blob.getName.endsWith("/"))
        None
      else
        Option(blob)
    }
    result.unNone
  }

  override def unsafeGetBlobBody(bucketName: GcsBucketName,
                                 blobName: GcsBlobName,
                                 traceId: Option[TraceId] = None,
                                 retryConfig: RetryConfig): F[Option[String]] =
    getBlobBody(bucketName, blobName, traceId, retryConfig).through(text.utf8Decode).compile.foldSemigroup

  override def getBlobBody(bucketName: GcsBucketName,
                           blobName: GcsBlobName,
                           traceId: Option[TraceId] = None,
                           retryConfig: RetryConfig): Stream[F, Byte] = {
    val getBlobs = blockingF(Async[F].delay(db.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_))

    for {
      blobOpt <- retryGoogleF(retryConfig)(
        getBlobs,
        traceId,
        s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)})"
      )
      r <- blobOpt match {
        case Some(blob) =>
          // implementation based on fs-blobstore
          fs2.io.unsafeReadInputStream(
            Channels
              .newInputStream {
                val reader = blob.reader()
                reader.setChunkSize(chunkSize)
                reader
              }
              .pure[F],
            chunkSize,
            blocker,
            closeAfterUse = true
          )
        case None => Stream.empty
      }
    } yield r
  }

  override def getBlob(bucketName: GcsBucketName,
                       blobName: GcsBlobName,
                       credentials: Option[Credentials] = None,
                       traceId: Option[TraceId] = None,
                       retryConfig: RetryConfig): Stream[F, Blob] = {
    val dbForCredential = credentials match {
      case Some(c) => db.getOptions.toBuilder.setCredentials(c).build().getService
      case None    => db
    }

    val getBlobs =
      blockingF(Async[F].delay(dbForCredential.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_))

    retryGoogleF(retryConfig)(
      getBlobs,
      traceId,
      s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)})"
    ).unNone
  }

  def downloadObject(blobId: BlobId,
                     path: Path,
                     traceId: Option[TraceId] = None,
                     retryConfig: RetryConfig): Stream[F, Unit] = {
    val downLoad = blockingF(Async[F].delay(db.get(blobId).downloadTo(path)))

    retryGoogleF(retryConfig)(downLoad, traceId, s"com.google.cloud.storage.Storage.get($blobId).download")
  }

  override def getObjectMetadata(bucketName: GcsBucketName,
                                 blobName: GcsBlobName,
                                 traceId: Option[TraceId],
                                 retryConfig: RetryConfig): Stream[F, GetMetadataResponse] = {
    val getBlobs = blockingF(Async[F].delay(db.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_))

    for {
      blobOpt <- retryGoogleF(retryConfig)(
        getBlobs,
        traceId,
        s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)})"
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

  //Overwrites all of the metadata on the GCS object with the provided metadata
  override def setObjectMetadata(bucketName: GcsBucketName,
                                 objectName: GcsBlobName,
                                 metadata: Map[String, String],
                                 traceId: Option[TraceId],
                                 retryConfig: RetryConfig): Stream[F, Unit] = {
    val blobId = BlobId.of(bucketName.value, objectName.value)
    val blobInfo = BlobInfo
      .newBuilder(blobId)
      .setMetadata(metadata.asJava)
      .build()

    val metadataUpdate = blockingF(Async[F].delay(db.update(blobInfo)))

    retryGoogleF(retryConfig)(metadataUpdate,
                              traceId,
                              s"com.google.cloud.storage.Storage.update($bucketName/${objectName.value})").void
  }

  override def createBlob(bucketName: GcsBucketName,
                          objectName: GcsBlobName,
                          objectContents: Array[Byte],
                          objectType: String = "text/plain",
                          metadata: Map[String, String] = Map.empty,
                          generation: Option[Long] = None,
                          traceId: Option[TraceId] = None,
                          retryConfig: RetryConfig): Stream[F, Blob] = {
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

    retryGoogleF(retryConfig)(storeObject,
                              traceId,
                              s"com.google.cloud.storage.Storage.create($bucketName/${objectName.value}, xxx)")
  }

  override def removeObject(bucketName: GcsBucketName,
                            blobName: GcsBlobName,
                            generation: Option[Long] = None,
                            traceId: Option[TraceId] = None,
                            retryConfig: RetryConfig): Stream[F, RemoveObjectResult] = {
    val deleteObject: F[Boolean] = generation match {
      case Some(g) =>
        val blobId = BlobId.of(bucketName.value, blobName.value, g)
        blockingF(Async[F].delay(db.delete(blobId, BlobSourceOption.generationMatch(g))))
      case None =>
        blockingF(Async[F].delay(db.delete(BlobId.of(bucketName.value, blobName.value))))
    }

    for {
      deleted <- retryGoogleF(retryConfig)(
        deleteObject,
        traceId,
        s"com.google.cloud.storage.Storage.delete(${bucketName.value}/${blobName.value})"
      )
    } yield RemoveObjectResult(deleted)
  }

  override def insertBucket(googleProject: GoogleProject,
                            bucketName: GcsBucketName,
                            acl: Option[NonEmptyList[Acl]] = None,
                            labels: Map[String, String] = Map.empty,
                            traceId: Option[TraceId] = None,
                            bucketPolicyOnlyEnabled: Boolean = false,
                            logBucket: Option[GcsBucketName] = None,
                            retryConfig: RetryConfig): Stream[F, Unit] = {

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

    logBucket.map { logBucketName =>
      val logging = BucketInfo.Logging.newBuilder().setLogBucket(logBucketName.value).build()
      bucketInfoBuilder.setLogging(logging)
    }

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

    val createBucket = blockingF(Async[F].delay(dbForProject.create(bucketInfo))).void.recoverWith {
      case e: com.google.cloud.storage.StorageException if (e.getCode == 409) =>
        StructuredLogger[F].info(s"$bucketName already exists")
    }

    retryGoogleF(retryConfig)(
      createBucket,
      traceId,
      s"com.google.cloud.storage.Storage.create($bucketInfo, ${googleProject.value})"
    )
  }

  override def deleteBucket(googleProject: GoogleProject,
                            bucketName: GcsBucketName,
                            isRecursive: Boolean,
                            bucketSourceOptions: List[BucketSourceOption] = List.empty,
                            traceId: Option[TraceId] = None,
                            retryConfig: RetryConfig = standardRetryConfig): Stream[F, Boolean] = {
    val dbForProject = db.getOptions.toBuilder.setProjectId(googleProject.value).build().getService

    val allBlobs = listBlobs(dbForProject, bucketName, List.empty, traceId, retryConfig, false).map(_.getBlobId)

    for {
      _ <- if (isRecursive) {
        val r = for {
          allBlobs <- allBlobs.compile.toList
          isSuccess <- if (allBlobs.isEmpty)
            Async[F].pure(true)
          else Async[F].delay(dbForProject.delete(allBlobs.asJava).asScala.toList.forall(x => x))
          _ <- if (isSuccess)
            logger.info(s"Successfully deleted all objects in ${bucketName.value}")
          else
            Async[F].raiseError(new RuntimeException(s"failed to delete all objects in ${bucketName.value}"))
        } yield ()
        Stream.eval(r)
      } else Stream.eval(Async[F].unit)
      deleteBucket = Async[F].delay(dbForProject.delete(bucketName.value, bucketSourceOptions: _*))
      res <- retryGoogleF(retryConfig)(
        deleteBucket,
        traceId,
        s"com.google.cloud.storage.Storage.delete(${bucketName.value}, ${bucketSourceOptions})"
      )
    } yield res
  }

  override def setBucketPolicyOnly(bucketName: GcsBucketName,
                                   bucketPolicyOnlyEnabled: Boolean,
                                   traceId: Option[TraceId] = None,
                                   retryConfig: RetryConfig): Stream[F, Unit] = {
    val iamConfiguration =
      BucketInfo.IamConfiguration.newBuilder().setIsUniformBucketLevelAccessEnabled(bucketPolicyOnlyEnabled).build()
    val updateBucket = blockingF(
      Async[F].delay(db.update(BucketInfo.newBuilder(bucketName.value).setIamConfiguration(iamConfiguration).build()))
    )

    retryGoogleF(retryConfig)(
      updateBucket,
      traceId,
      s"com.google.cloud.storage.Storage.update($bucketName)"
    ).void
  }

  override def setBucketLabels(bucketName: GcsBucketName,
                               labels: Map[String, String],
                               traceId: Option[TraceId] = None,
                               retryConfig: RetryConfig): Stream[F, Unit] = {
    val updateBucket = blockingF(
      Async[F].delay(db.update(BucketInfo.newBuilder(bucketName.value).setLabels(labels.asJava).build()))
    )

    retryGoogleF(retryConfig)(
      updateBucket,
      traceId,
      s"com.google.cloud.storage.Storage.update($bucketName, $labels)"
    ).void
  }

  override def setIamPolicy(bucketName: GcsBucketName,
                            roles: Map[StorageRole, NonEmptyList[Identity]],
                            traceId: Option[TraceId] = None,
                            retryConfig: RetryConfig): Stream[F, Unit] = {
    val getAndSetIamPolicy = for {
      policy <- blockingF(Async[F].delay(db.getIamPolicy(bucketName.value)))
      policyBuilder = policy.toBuilder()
      updatedPolicy = roles
        .foldLeft(policyBuilder)(
          (currentBuilder, item) => currentBuilder.addIdentity(Role.of(item._1.name), item._2.head, item._2.tail: _*)
        )
        .build()
      _ <- blockingF(Async[F].delay(db.setIamPolicy(bucketName.value, updatedPolicy)))
    } yield ()

    retryGoogleF(retryConfig)(
      getAndSetIamPolicy,
      traceId,
      s"com.google.cloud.storage.Storage.getIamPolicy(${bucketName}), com.google.cloud.storage.Storage.setIamPolicy(${bucketName}, $roles)"
    )
  }

  override def getIamPolicy(bucketName: GcsBucketName,
                            traceId: Option[TraceId] = None,
                            retryConfig: RetryConfig): Stream[F, Policy] = {
    val getIamPolicy = for {
      policy <- blockingF(Async[F].delay(db.getIamPolicy(bucketName.value)))
    } yield policy

    retryGoogleF(retryConfig)(
      getIamPolicy,
      traceId,
      s"com.google.cloud.storage.Storage.getIamPolicy(${bucketName})"
    )
  }

  override def setBucketLifecycle(bucketName: GcsBucketName,
                                  lifecycleRules: List[LifecycleRule],
                                  traceId: Option[TraceId] = None,
                                  retryConfig: RetryConfig): Stream[F, Unit] = {
    val bucketInfo = BucketInfo
      .of(bucketName.value)
      .toBuilder
      .setLifecycleRules(lifecycleRules.asJava)
      .build()
    retryGoogleF(retryConfig)(blockingF(Async[F].delay(db.update(bucketInfo))),
                              traceId,
                              s"com.google.cloud.storage.Storage.update($bucketInfo)").void
  }

  private def listBlobs(db: Storage,
                        bucketName: GcsBucketName,
                        blobListOptions: List[BlobListOption],
                        traceId: Option[TraceId],
                        retryConfig: RetryConfig,
                        ifErrorWhenBucketNotFound: Boolean): Stream[F, Blob] = {
    val listFirstPage =
      Async[F].delay(db.list(bucketName.value, blobListOptions: _*)).map(p => Option(p)).handleErrorWith {
        case e: com.google.cloud.storage.StorageException if (e.getCode == 404) =>
          //This can happen if the bucket doesn't exist
          if (ifErrorWhenBucketNotFound)
            Async[F].raiseError(e)
          else
            Async[F].pure(None)
      }

    for {
      firstPage <- retryGoogleF(retryConfig)(
        blockingF(listFirstPage),
        traceId,
        s"com.google.cloud.storage.Storage.list($bucketName, ${blobListOptions})"
      ).unNone
      page <- Stream.unfoldEval(firstPage) { currentPage =>
        Option(currentPage).traverse { p =>
          val fetchNext = retryGoogleF(retryConfig)(
            blockingF(Async[F].delay(p.getNextPage)),
            traceId,
            s"com.google.api.gax.paging.Page.getNextPage"
          )
          fetchNext.compile.lastOrError.map(next => (p, next))
        }
      }
      blob <- Stream.fromIterator[F](page.getValues.iterator().asScala).map(b => Option(b)).unNone
    } yield blob
  }

  private def blockingF[A](fa: F[A]): F[A] = blockerBound match {
    case None    => blocker.blockOn(fa)
    case Some(s) => s.withPermit(blocker.blockOn(fa))
  }

  private val chunkSize = 1024 * 1024 * 2 // com.google.cloud.storage.BlobReadChannel.DEFAULT_CHUNK_SIZE
}

object GoogleStorageInterpreter {
  def apply[F[_]: Timer: Async: ContextShift: StructuredLogger](
    db: Storage,
    blocker: Blocker,
    blockerBound: Option[Semaphore[F]]
  ): GoogleStorageInterpreter[F] =
    new GoogleStorageInterpreter(db, blocker, blockerBound)

  def storage[F[_]: Sync: ContextShift](
    pathToJson: String,
    blocker: Blocker,
    blockerBound: Option[Semaphore[F]],
    project: Option[GoogleProject] = None // legacy credential file doesn't have `project_id` field. Hence we need to pass in explicitly
  ): Resource[F, Storage] =
    for {
      credential <- org.broadinstitute.dsde.workbench.util2.readFile(pathToJson)
      project <- project match { //Use explicitly passed in project if it's defined; else use `project_id` in json credential; if neither has project defined, raise error
        case Some(p) => Resource.pure[F, GoogleProject](p)
        case None    => Resource.liftF(parseProject(pathToJson, blocker).compile.lastOrError)
      }
      db <- Resource.liftF(
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

  implicit val googleProjectDecoder: Decoder[GoogleProject] = Decoder.forProduct1(
    "project_id"
  )(GoogleProject.apply)

  def parseProject[F[_]: ContextShift: Sync](pathToJson: String, blocker: Blocker): Stream[F, GoogleProject] =
    fs2.io.file
      .readAll[F](Paths.get(pathToJson), blocker, 4096)
      .through(byteStreamParser)
      .through(decoder[F, GoogleProject])
}
