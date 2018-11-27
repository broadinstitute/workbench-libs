package org.broadinstitute.dsde.workbench.google

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

private[google] class GoogleStorageIO(db: Storage,
                                      blockingEc: ExecutionContext,
                                      maxPageSize: Long,
                                      retryConfig: RetryConfig
                                     )(
  implicit cs: ContextShift[IO],
  timer: Timer[IO]
) extends GoogleStorageAlg[IO] {
  private def retryStorageIO[A](ioa: IO[A]): Stream[IO, A] = Stream.retry(ioa, retryConfig.retryInitialDelay, retryConfig.retryNextDelay, retryConfig.maxAttempts, retryConfig.retryable)

  override def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String): Stream[IO, GcsObjectName] = {
    val retrievePages = cs.evalOn(blockingEc)(IO(db.list(bucketName.value, BlobListOption.prefix(objectNamePrefix), BlobListOption.pageSize(maxPageSize), BlobListOption.currentDirectory())))
    for {
      pages <- retryStorageIO(retrievePages)
      objects <- Stream.fromIterator[IO, Blob](pages.iterateAll().iterator().asScala).map{
        blob => GcsObjectName(blob.getName, Instant.ofEpochMilli(blob.getCreateTime))
      }
    } yield objects
  }

  override def unsafeListObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String): IO[List[GcsObjectName]] = listObjectsWithPrefix(bucketName, objectNamePrefix).map(List(_)).compile.foldMonoid

  override def unsafeGetObject(bucketName: GcsBucketName, blobName: GcsBlobName): IO[Option[String]] = {
    val getBlobs = cs.evalOn(blockingEc)(IO(db.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_))

    val res = for {
      blob <- OptionT(retryStorageIO(getBlobs))
      r <- OptionT.liftF(Stream.emits(blob.getContent()).covary[IO].through(text.utf8Decode))
    } yield r
    res.value.compile.foldMonoid
  }

  override def storeObject(bucketName: GcsBucketName, objectName: GcsBlobName, objectContents: Array[Byte], objectType: String): IO[Unit] = {
    val blobInfo = BlobInfo.newBuilder(bucketName.value, objectName.value).setContentType(objectType).build()
    val storeObject = cs.evalOn(blockingEc)(IO(db.create(blobInfo, objectContents)))
    for {
      _ <- retryStorageIO(storeObject).compile.drain
    } yield ()
  }

  /**
    * @return true if deleted; false if not found
    */
  override def removeObject(bucketName: GcsBucketName, blobName: GcsBlobName): IO[Boolean] = {
    val deleteObject = cs.evalOn(blockingEc)(IO(db.delete(BlobId.of(bucketName.value, blobName.value))))
    for {
      deleted <- retryStorageIO(deleteObject).compile.lastOrError
    } yield deleted
  }

  override def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, acl: List[Acl]): IO[Unit] = {
    val bucketInfo = BucketInfo.of(bucketName.value)
      .toBuilder
      .setAcl(acl.asJava)
      .setDefaultAcl(acl.asJava)
      .build()

    retryStorageIO(cs.evalOn(blockingEc)(IO(db.create(bucketInfo, BucketTargetOption.userProject(billingProject.value))))).compile.drain
  }

  override def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[LifecycleRule]): IO[Unit] = {
    val bucketInfo = BucketInfo.of(bucketName.value)
      .toBuilder
      .setLifecycleRules(lifecycleRules.asJava)
      .build()
    retryStorageIO(cs.evalOn(blockingEc)(IO(db.update(bucketInfo)))).compile.drain
  }
}

object GoogleStorageInterpreters {
  private val storageRetryableCode = Set(408, 429, 500, 502, 503, 504) //error codes are copied from com.google.cloud.storage.StorageException.RETRYABLE_ERRORS

  val defaultRetryConfig = RetryConfig(
    2 seconds,
    x => x * 2, 5,
    {
      case e: StorageException => if(storageRetryableCode.contains(e.getCode)) true else false
      case other@_ => false
    }
  )

  def ioStorage(db: Storage, blockingEc: ExecutionContext, maxPageSize: Long = 1000, retryConfig: RetryConfig = defaultRetryConfig)(
    implicit cs: ContextShift[IO],
    timer: Timer[IO]
  ): GoogleStorageAlg[IO] = new GoogleStorageIO(db, blockingEc, maxPageSize, retryConfig)

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

final case class RetryConfig(retryInitialDelay: FiniteDuration, retryNextDelay: FiniteDuration => FiniteDuration, maxAttempts: Int, retryable: Throwable => Boolean)