package org.broadinstitute.dsde.workbench.google2


import cats.Monad
import cats.data.NonEmptyList
import cats.effect.std.{Semaphore, UUIDGen}
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import com.google.cloud.Identity
import com.google.cloud.storage.StorageOptions
import org.broadinstitute.dsde.workbench.google2.GetMetadataResponse.NotFound
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.google._
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import java.time.LocalDate

class GoogleStorageTransferInterpreterSpec extends AsyncFlatSpec with Matchers with WorkbenchTestSuite {

  val db = StorageOptions.getDefaultInstance.getService
  val semaphore = Semaphore[IO](1).unsafeRunSync
  val storage = GoogleStorageInterpreter[IO](db, Some(semaphore))
  val sts = new GoogleStorageTransferInterpreter[IO]

  val srcBucket = GcsBucketName("workbench-libs-sts-test")
  val googleProject = GoogleProject("broad-dsde-dev")

  def randomize(name: String): IO[String] =
    UUIDGen[IO].randomUUID.map(name ++ _.toString.replace("-", ""))

  def temporaryGcsBucket[A](project: GoogleProject, bucketPrefix: String): Resource[IO, GcsBucketName] =
    Resource.make(
      for {
        bucketName <- randomize(bucketPrefix).map(GcsBucketName)
        _ <- storage.insertBucket(project, bucketName).compile.drain
      } yield bucketName
    )(storage.deleteBucket(project, _, isRecursive = true).compile.drain)

  "getStsServiceAccount" should "return a google-owned SA specific to the google project" in ioAssertion {
    sts.getStsServiceAccount(googleProject) map { case ServiceAccount(_, email, _) =>
      email.value should include("storage-transfer")
      email.value should endWith("gserviceaccount.com")
    }
  }

  "createTransferJob" should "create a storage transfer service job from one bucket to another" in ioAssertion {
    temporaryGcsBucket(googleProject, "workbench-libs-").use { dstBucket =>
      for {
        serviceAccount <- sts.getStsServiceAccount(googleProject)
        serviceAccountList = NonEmptyList.one(Identity.serviceAccount(serviceAccount.email.value))

        // STS Service Account requires "Storage Object Viewer" and "Storage Legacy Bucket Reader"
        // roles on the bucket it transfers to
        _ <- storage.setIamPolicy(srcBucket, Map(
          (StorageRole.LegacyBucketReader, serviceAccountList),
          (StorageRole.ObjectViewer, serviceAccountList)
        )).compile.drain

        // STS Service Account requires "Storage Object Creator" and "Storage Legacy Bucket Writer"
        // roles on the bucket it transfers to
        _ <- storage.setIamPolicy(dstBucket, Map(
          (StorageRole.LegacyBucketWriter, serviceAccountList),
          (StorageRole.ObjectCreator, serviceAccountList)
        )).compile.drain

        jobName <- randomize("workbench-libs-sts-test")
          .map(TransferJobName(_))

        _ <- sts.createTransferJob(
          jobName,
          "testing creating a storage transfer job",
          googleProject,
          srcBucket,
          dstBucket,
          TransferOnce(LocalDate.now)
        )

        _ <- IO.sleep(5.seconds).untilM_(
          sts.listTransferOperations(jobName, googleProject).map({
            case Seq() => false
            case xs => xs.forall(_.getDone)
          })
        )

        obj <- storage.getObjectMetadata(dstBucket, GcsBlobName("test_entity.tsv"))
          .compile
          .lastOrError

      } yield obj should not be NotFound
    }
  }

}