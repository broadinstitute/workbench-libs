package org.broadinstitute.dsde.workbench.google

import java.time.Instant

import cats.data.OptionT
import cats.effect.{ContextShift, IO, Resource, Sync}
import cats.implicits._
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.Storage.{BlobListOption, BucketTargetOption}
import com.google.cloud.storage.{Acl, Blob, BlobId, BlobInfo, BucketInfo, Storage, StorageOptions}
import fs2.{Stream, text}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object GoogleStorageInterpreters {
  def ioStorage(db: Storage, blockingEc: ExecutionContext, maxPageSize: Long = 1000)(
    implicit cs: ContextShift[IO]
  ): GoogleStorageAlg[IO] = new GoogleStorageAlg[IO] {
    override def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String): Stream[IO, GcsObjectName] = for {
        pages <- Stream.eval(cs.evalOn(blockingEc)(IO(db.list(bucketName.value, BlobListOption.prefix(objectNamePrefix), BlobListOption.pageSize(maxPageSize), BlobListOption.currentDirectory()))))
        objects <- Stream.fromIterator[IO, Blob](pages.iterateAll().iterator().asScala).map{
          blob => GcsObjectName(blob.getName, Instant.ofEpochMilli(blob.getCreateTime))
        }
      } yield objects

    override def unsafeListObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String): IO[List[GcsObjectName]] = listObjectsWithPrefix(bucketName, objectNamePrefix).map(List(_)).compile.foldMonoid

    override def unsafeGetObject(bucketName: GcsBucketName, blobName: GcsBlobName): IO[Option[String]] = {
      val res = for {
        blob <- OptionT(cs.evalOn(blockingEc)(IO(db.get(BlobId.of(bucketName.value, blobName.value)))).map(Option(_)))
        r <- OptionT.liftF(Stream.emits(blob.getContent()).covary[IO].through(text.utf8Decode).compile.foldMonoid)
      } yield r
      res.value
    }

    override def storeObject(bucketName: GcsBucketName, objectName: GcsBlobName, objectContents: Array[Byte], objectType: String): IO[Unit] = {
      val blobInfo = BlobInfo.newBuilder(bucketName.value, objectName.value).setContentType(objectType).build()
      for {
        _ <- cs.evalOn(blockingEc)(IO(db.create(blobInfo, objectContents)))
      } yield ()
    }

    /**
      * @return true if deleted; false if not found
      */
    override def removeObject(bucketName: GcsBucketName, blobName: GcsBlobName): IO[Boolean] = {
      for {
        deleted <- cs.evalOn(blockingEc)(IO(db.delete(BlobId.of(bucketName.value, blobName.value))))
      } yield deleted
    }

    override def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, acl: List[Acl]): IO[Unit] = {
      val bucketInfo = BucketInfo.of(bucketName.value)
        .toBuilder
        .setAcl(acl.asJava)
        .setDefaultAcl(acl.asJava)
        .build()

      cs.evalOn(blockingEc)(IO(db.create(bucketInfo, BucketTargetOption.userProject(billingProject.value))))
    }

    override def setBucketLifecycle(bucketName: GcsBucketName, lifecycleRules: List[LifecycleRule]): IO[Unit] = {
      val bucketInfo = BucketInfo.of(bucketName.value)
        .toBuilder
        .setLifecycleRules(lifecycleRules.asJava)
        .build()
      cs.evalOn(blockingEc)(IO(db.update(bucketInfo))).void
    }
  }

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
