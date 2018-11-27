package org.broadinstitute.dsde.workbench
package google2

import java.time.Instant

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.Storage.{BlobListOption, BucketTargetOption}
import com.google.cloud.storage.{Acl, Blob, BlobId, BlobInfo, BucketInfo, Storage, StorageException, StorageOptions}
import fs2.{Stream, text}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

private[google2] class GoogleStorageInterpreter[F[_]](db: Storage,
                                      blockingEc: ExecutionContext,
                                      retryConfig: RetryConfig
                                     )(
  implicit cs: ContextShift[F],
  timer: Timer[F],
  asyncF: Async[F]
) extends GoogleStorageService[F] {
  private def retryStorageF[A]: F[A] => Stream[F, A] = fa => retryGoogleF(retryConfig)(fa)

  override def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String, maxPageSize: Long = 1000): Stream[F, GcsObjectName] = {
    for {
      firstPage <- retryStorageF(blockingF(asyncF.delay(db.list(bucketName.value, BlobListOption.prefix(objectNamePrefix), BlobListOption.pageSize(maxPageSize.longValue()), BlobListOption.currentDirectory()))))
      page <- Stream.unfoldEval(firstPage){
        currentPage =>
          Option(currentPage).traverse{
            p =>
              retryStorageF(blockingF(asyncF.delay(p.getNextPage))).compile.lastOrError.map(next => (p, next))
          }
      }
      objects <- Stream.fromIterator[F, Blob](page.getValues.iterator().asScala).map{
        blob => GcsObjectName(blob.getName, Instant.ofEpochMilli(blob.getCreateTime))
      }
    } yield objects
  }

  override def unsafeGetObject(bucketName: GcsBucketName, blobName: GcsBlobName): F[Option[String]] = {
    val getBlobs = blockingF(asyncF.delay(db.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_))

    val res = for {
      blob <- OptionT(retryStorageF(getBlobs))
      r <- OptionT.liftF(Stream.emits(blob.getContent()).covary[F].through(text.utf8Decode))
    } yield r
    res.value.compile.foldMonoid
  }

  override def storeObject(bucketName: GcsBucketName, objectName: GcsBlobName, objectContents: Array[Byte], objectType: String): F[Unit] = {
    val blobInfo = BlobInfo.newBuilder(bucketName.value, objectName.value).setContentType(objectType).build()

    val storeObject = blockingF(asyncF.delay(db.create(blobInfo, objectContents)))
    for {
      _ <- retryStorageF(storeObject).compile.drain
    } yield ()
  }

  override def removeObject(bucketName: GcsBucketName, blobName: GcsBlobName): F[RemoveObjectResult] = {
    val deleteObject = blockingF(asyncF.delay(db.delete(BlobId.of(bucketName.value, blobName.value))))
    for {
      deleted <- retryStorageF(deleteObject).compile.lastOrError
    } yield RemoveObjectResult(deleted)
  }

  override def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, acl: List[Acl]): F[Unit] = {
    val bucketInfo = BucketInfo.of(bucketName.value)
      .toBuilder
      .setAcl(acl.asJava)
      .setDefaultAcl(acl.asJava)
      .build()

    retryStorageF(blockingF(asyncF.delay(db.create(bucketInfo, BucketTargetOption.userProject(billingProject.value))))).compile.drain
  }

  override def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[LifecycleRule]): F[Unit] = {
    val bucketInfo = BucketInfo.of(bucketName.value)
      .toBuilder
      .setLifecycleRules(lifecycleRules.asJava)
      .build()
    retryStorageF(blockingF(asyncF.delay(db.update(bucketInfo)))).compile.drain
  }

  private def blockingF[A](fa: F[A]): F[A] = cs.evalOn(blockingEc)(fa)
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

  def apply[F[_]: Timer: Async: ContextShift](db: Storage, blockingEc: ExecutionContext, retryConfig: RetryConfig = defaultRetryConfig): GoogleStorageInterpreter[F] =
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
