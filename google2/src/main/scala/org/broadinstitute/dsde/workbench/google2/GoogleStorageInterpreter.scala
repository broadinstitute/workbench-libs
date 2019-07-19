package org.broadinstitute.dsde.workbench
package google2

import java.nio.file.{Path, Paths}
import java.time.Instant

import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import com.google.cloud.Policy
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.{Identity, Role}
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.Storage.{BlobListOption, BlobSourceOption, BlobTargetOption}
import com.google.cloud.storage.{Acl, Blob, BlobId, BlobInfo, BucketInfo, Storage, StorageException, StorageOptions}
import fs2.{Stream, text}
import io.chrisdavenport.linebacker.Linebacker
import io.chrisdavenport.log4cats.Logger
import io.circe.Decoder
import io.circe.fs2._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

private[google2] class GoogleStorageInterpreter[F[_]: ContextShift: Timer: Async: Logger: Linebacker](db: Storage,
                                      retryConfig: RetryConfig
                                     ) extends GoogleStorageService[F] {
  private def retryStorageF[A]: (F[A], Option[TraceId], String) => Stream[F, A] = retryGoogleF(retryConfig)

  override def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000, traceId: Option[TraceId] = None): Stream[F, GcsObjectName] = {
    for {
      firstPage <- retryStorageF(
        blockingF(Async[F].delay(db.list(bucketName.value, BlobListOption.prefix(objectNamePrefix), BlobListOption.pageSize(maxPageSize.longValue()), BlobListOption.currentDirectory()))),
        traceId,
        s"com.google.cloud.storage.Storage.list($bucketName, ${BlobListOption.prefix(objectNamePrefix)}, ${BlobListOption.pageSize(maxPageSize.longValue())}, ${BlobListOption.currentDirectory()})"
      )
      page <- Stream.unfoldEval(firstPage){
        currentPage =>
          Option(currentPage).traverse{
            p =>
              val fetchNext = retryStorageF(
                blockingF(Async[F].delay(p.getNextPage)),
                traceId,
                s"com.google.api.gax.paging.Page.getNextPage"
              )
              fetchNext.compile.lastOrError.map(next => (p, next))
          }
      }
      objects <- Stream.fromIterator[F, Blob](page.getValues.iterator().asScala).map {
        blob => GcsObjectName(blob.getName, Instant.ofEpochMilli(blob.getCreateTime))
      }
    } yield objects
  }

  override def unsafeGetBlobBody(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): F[Option[String]] = {
    getBlobBody(bucketName, blobName, traceId).through(text.utf8Decode).compile.foldSemigroup
  }

  override def getBlobBody(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): Stream[F, Byte] = {
    val getBlobs = blockingF(Async[F].delay(db.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_))

    for {
      blobOpt <- retryStorageF(getBlobs, traceId, s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)})")
      r <- blobOpt match {
        case Some(blob) => Stream.emits(blob.getContent()).covary[F]
        case None => Stream.empty
      }
    } yield r
  }

  override def getBlob(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): Stream[F, Blob] = {
    val getBlobs = blockingF(Async[F].delay(db.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_))

    retryStorageF(getBlobs, traceId, s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)})").unNone
  }

  def downloadObject(blobId: BlobId, path: Path, traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val downLoad = blockingF(Async[F].delay(db.get(blobId).downloadTo(path)))

    retryStorageF(downLoad, traceId, s"com.google.cloud.storage.Storage.get($blobId).download")
  }

  override def getObjectMetadata(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId]): Stream[F, GetMetadataResponse] = {
    val getBlobs = blockingF(Async[F].delay(db.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_))

    for {
      blobOpt <- retryStorageF(getBlobs, traceId, s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)})")
      r = blobOpt match {
        case Some(blob) =>
          Option(blob.getMetadata) match {
            case None => GetMetadataResponse.Metadata(Crc32(blob.getCrc32c), Map.empty, blob.getGeneration)
            case Some(x) => GetMetadataResponse.Metadata(Crc32(blob.getCrc32c), x.asScala.toMap, blob.getGeneration)
          }
        case None => GetMetadataResponse.NotFound
      }
    } yield r
  }

  //Overwrites all of the metadata on the GCS object with the provided metadata
  override def setObjectMetadata(bucketName: GcsBucketName, objectName: GcsBlobName, metadata: Map[String, String], traceId: Option[TraceId]): Stream[F, Unit] = {
    val blobId = BlobId.of(bucketName.value, objectName.value)
    val blobInfo = BlobInfo
      .newBuilder(blobId)
      .setMetadata(metadata.asJava)
      .build()

    val metadataUpdate = blockingF(Async[F].delay(db.update(blobInfo)))

    retryStorageF(metadataUpdate, traceId, s"com.google.cloud.storage.Storage.update($bucketName/${objectName.value})").void
  }

  override def createBlob(bucketName: GcsBucketName,
                           objectName: GcsBlobName,
                           objectContents: Array[Byte],
                           objectType: String = "text/plain",
                           metadata: Map[String, String] = Map.empty,
                           generation: Option[Long] = None,
                           traceId: Option[TraceId] = None): Stream[F, Blob] = {
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

    retryStorageF(storeObject, traceId, s"com.google.cloud.storage.Storage.create($bucketName/${objectName.value}, xxx)")
  }

  override def removeObject(bucketName: GcsBucketName,
                            blobName: GcsBlobName,
                            generation: Option[Long] = None,
                            traceId: Option[TraceId] = None): Stream[F, RemoveObjectResult] = {
    val deleteObject: F[Boolean] = generation match {
      case Some(g) =>
        val blobId = BlobId.of(bucketName.value, blobName.value, g)
        blockingF(Async[F].delay(db.delete(blobId, BlobSourceOption.generationMatch(g))))
      case None =>
        blockingF(Async[F].delay(db.delete(BlobId.of(bucketName.value, blobName.value))))
    }

    for {
      deleted <- retryStorageF(deleteObject, traceId, s"com.google.cloud.storage.Storage.delete(${bucketName.value}/${blobName.value})")
    } yield RemoveObjectResult(deleted)
  }

  override def insertBucket(googleProject: GoogleProject, bucketName: GcsBucketName, acl: Option[NonEmptyList[Acl]] = None, labels: Map[String, String] = Map.empty, traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val bucketInfoBuilder = BucketInfo.of(bucketName.value).toBuilder.setLabels(labels.asJava)
    val bucketInfo = acl.map{
      aclList =>
        val acls = aclList.toList.asJava
        bucketInfoBuilder
          .setAcl(acls)
          .setDefaultAcl(acls)
          .build()
    }.getOrElse(bucketInfoBuilder.build())

    val dbForProject = db.getOptions.toBuilder.setProjectId(googleProject.value).build().getService

    val createBucket = blockingF(Async[F].delay(dbForProject.create(bucketInfo))).void.handleErrorWith {
      case e: com.google.cloud.storage.StorageException if(e.getCode == 409) =>
        Logger[F].info(s"$bucketName already exists")
    }

    retryStorageF(
      createBucket,
      traceId,
      s"com.google.cloud.storage.Storage.create($bucketInfo, ${googleProject.value})"
    )
  }

  override def setBucketPolicyOnly(bucketName: GcsBucketName, bucketPolicyOnlyEnabled: Boolean, traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val iamConfiguration = BucketInfo.IamConfiguration.newBuilder().setIsBucketPolicyOnlyEnabled(bucketPolicyOnlyEnabled).build()
    val updateBucket = blockingF(Async[F].delay(db.update(BucketInfo.newBuilder(bucketName.value).setIamConfiguration(iamConfiguration).build())))

    retryStorageF(
      updateBucket,
      traceId,
      s"com.google.cloud.storage.Storage.update($bucketName)"
    ).void
  }

  override def setBucketLabels(bucketName: GcsBucketName, labels: Map[String, String], traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val updateBucket = blockingF(Async[F].delay(db.update(BucketInfo.newBuilder(bucketName.value).setLabels(labels.asJava).build())))

    retryStorageF(
      updateBucket,
      traceId,
      s"com.google.cloud.storage.Storage.update($bucketName)"
    ).void
  }

  override def setIamPolicy(bucketName: GcsBucketName, roles: Map[StorageRole, NonEmptyList[Identity]], traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val getAndSetIamPolicy = for {
      policy <- blockingF(Async[F].delay(db.getIamPolicy(bucketName.value)))
      policyBuilder = policy.toBuilder()
      updatedPolicy = roles.foldLeft(policyBuilder)((currentBuilder, item) => currentBuilder.addIdentity(Role.of(item._1.name), item._2.head, item._2.tail: _*)).build()
      _ <- blockingF(Async[F].delay(db.setIamPolicy(bucketName.value, updatedPolicy)))
    } yield ()

    retryStorageF(
      getAndSetIamPolicy,
      traceId,
      s"com.google.cloud.storage.Storage.getIamPolicy(${bucketName}), com.google.cloud.storage.Storage.setIamPolicy(${bucketName}, $roles)"
    )
  }

  override def getIamPolicy(bucketName: GcsBucketName, traceId: Option[TraceId] = None): Stream[F, Policy] = {
    val getIamPolicy = for {
      policy <- blockingF(Async[F].delay(db.getIamPolicy(bucketName.value)))
    } yield policy

    retryStorageF(
      getIamPolicy,
      traceId,
      s"com.google.cloud.storage.Storage.getIamPolicy(${bucketName})"
    )
  }

  override def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[LifecycleRule], traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val bucketInfo = BucketInfo.of(bucketName.value)
      .toBuilder
      .setLifecycleRules(lifecycleRules.asJava)
      .build()
    retryStorageF(blockingF(Async[F].delay(db.update(bucketInfo))), traceId, s"com.google.cloud.storage.Storage.update($bucketInfo)").void
  }

  private def blockingF[A](fa: F[A]): F[A] = Linebacker[F].blockCS(fa)//ContextShift[F].evalOn(blockingEc)(fa)
}

object GoogleStorageInterpreter {
  private val storageRetryableCode = Set(408, 429, 500, 502, 503, 504) //error codes are copied from com.google.cloud.storage.StorageException.RETRYABLE_ERRORS

  val defaultRetryConfig = RetryConfig(
    org.broadinstitute.dsde.workbench.util.addJitter(1 seconds, 1 seconds),
    x => x * 2,
    5,
    {
      case e: StorageException => storageRetryableCode.contains(e.getCode)
      case other@_ => false
    }
  )

  def apply[F[_]: Timer: Async: ContextShift: Logger: Linebacker](db: Storage, retryConfig: RetryConfig): GoogleStorageInterpreter[F] =
    new GoogleStorageInterpreter(db, retryConfig)

  def storage[F[_]: Sync: ContextShift](
      pathToJson: String,
      blockingExecutionContext: ExecutionContext,
      project: Option[GoogleProject] = None // legacy credential file doesn't have `project_id` field. Hence we need to pass in explicitly
  ): Resource[F, Storage] =
    for {
      credential <- org.broadinstitute.dsde.workbench.util.readFile(pathToJson)
      project <- project match { //Use explicitly passed in project if it's defined; else use `project_id` in json credential; if neither has project defined, raise error
        case Some(p) => Resource.pure[F, GoogleProject](p)
        case None => Resource.liftF(parseProject(pathToJson, blockingExecutionContext).compile.lastOrError)
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

  def parseProject[F[_]: ContextShift: Sync](pathToJson: String, blockingExecutionContext: ExecutionContext = global): Stream[F, GoogleProject] =
     fs2.io.file.readAll[F](Paths.get(pathToJson), blockingExecutionContext, 4096)
          .through(byteStreamParser)
          .through(decoder[F, GoogleProject])
}
