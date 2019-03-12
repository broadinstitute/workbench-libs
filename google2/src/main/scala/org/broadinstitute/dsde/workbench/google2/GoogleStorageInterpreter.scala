package org.broadinstitute.dsde.workbench
package google2

import java.time.Instant

import cats.effect._
import cats.implicits._
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.Storage.{BlobListOption, BucketTargetOption}
import com.google.cloud.storage.{Acl, Blob, BlobId, BlobInfo, BucketInfo, Storage, StorageException, StorageOptions}
import fs2.{Stream, text}
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

private[google2] class GoogleStorageInterpreter[F[_]: ContextShift: Timer: Async: Logger](db: Storage,
                                      blockingEc: ExecutionContext,
                                      retryConfig: RetryConfig
                                     )extends GoogleStorageService[F] {
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
      objects <- Stream.fromIterator[F, Blob](page.getValues.iterator().asScala).map{
        blob => GcsObjectName(blob.getName, Instant.ofEpochMilli(blob.getCreateTime))
      }
    } yield objects
  }

  override def unsafeGetObject(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): F[Option[String]] = {
    getObject(bucketName, blobName, traceId).through(text.utf8Decode).compile.foldSemigroup
  }

  override def getObject(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): Stream[F, Byte] = {
    val getBlobs = blockingF(Async[F].delay(db.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_))

    for {
      blobOpt <- retryStorageF(getBlobs, traceId, s"com.google.cloud.storage.Storage.get(${BlobId.of(bucketName.value, blobName.value)})")
      r <- blobOpt match {
        case Some(blob) => Stream.emits(blob.getContent()).covary[F]
        case None => Stream.empty
      }
    } yield r
  }

  override def storeObject(bucketName: GcsBucketName, objectName: GcsBlobName, objectContents: Array[Byte], objectType: String, traceId: Option[TraceId] = None): F[Unit] = {
    val blobInfo = BlobInfo.newBuilder(bucketName.value, objectName.value).setContentType(objectType).build()

    val storeObject = blockingF(Async[F].delay(db.create(blobInfo, objectContents)))
    for {
      _ <- retryStorageF(storeObject, traceId, s"com.google.cloud.storage.Storage.create($bucketName/$objectName, xxx)").compile.drain
    } yield ()
  }

  override def removeObject(bucketName: GcsBucketName, blobName: GcsBlobName, traceId: Option[TraceId] = None): F[RemoveObjectResult] = {
    val deleteObject = blockingF(Async[F].delay(db.delete(BlobId.of(bucketName.value, blobName.value))))
    for {
      deleted <- retryStorageF(deleteObject, traceId, s"com.google.cloud.storage.Storage.delete($bucketName/$blobName)").compile.lastOrError
    } yield RemoveObjectResult(deleted)
  }

  override def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, acl: List[Acl], traceId: Option[TraceId] = None): F[Unit] = {
    val bucketInfo = BucketInfo.of(bucketName.value)
      .toBuilder
      .setAcl(acl.asJava)
      .setDefaultAcl(acl.asJava)
      .build()

    retryStorageF(
      blockingF(Async[F].delay(db.create(bucketInfo, BucketTargetOption.userProject(billingProject.value)))),
      None,
      s"com.google.cloud.storage.Storage.create($bucketInfo, ${billingProject})"
    ).compile.drain
  }

  override def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[LifecycleRule], traceId: Option[TraceId] = None): F[Unit] = {
    val bucketInfo = BucketInfo.of(bucketName.value)
      .toBuilder
      .setLifecycleRules(lifecycleRules.asJava)
      .build()
    retryStorageF(blockingF(Async[F].delay(db.update(bucketInfo))), traceId, s"com.google.cloud.storage.Storage.update($bucketInfo)").compile.drain
  }

  private def blockingF[A](fa: F[A]): F[A] = ContextShift[F].evalOn(blockingEc)(fa)
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

  def apply[F[_]: Timer: Async: ContextShift: Logger](db: Storage, blockingEc: ExecutionContext, retryConfig: RetryConfig): GoogleStorageInterpreter[F] =
    new GoogleStorageInterpreter(db, blockingEc, retryConfig)

  def storage[F[_]](
      pathToJson: String
  )(implicit sf: Sync[F]): Resource[F, Storage] =
    for {
      credential <- org.broadinstitute.dsde.workbench.util.readFile(pathToJson)
      db <- Resource.liftF(
        sf.delay(
          StorageOptions
            .newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(credential))
            .build()
            .getService
        )
      )
    } yield db
}
