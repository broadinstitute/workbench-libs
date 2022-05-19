package org.broadinstitute.dsde.workbench.google2

import cats.data.NonEmptyList
import cats.effect.std.{Semaphore, UUIDGen}
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.implicits._
import com.google.cloud.Identity
import com.google.cloud.storage.StorageOptions
import org.broadinstitute.dsde.workbench.google2.GetMetadataResponse.{Metadata, NotFound}
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService.ObjectDeletionOption.DeleteSourceObjectsAfterTransfer
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.broadinstitute.dsde.workbench.model.google._
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class GoogleStorageTransferServiceSpec extends AsyncFlatSpec with Matchers with WorkbenchTestSuite {

  val db = StorageOptions.getDefaultInstance.getService
  val semaphore = Semaphore[IO](1).unsafeRunSync
  val storage = GoogleStorageInterpreter[IO](db, Some(semaphore))

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

  "JobName" should """fail when the name is not prefixed with "transferJobs/"""" ignore ioAssertion {
    randomize("test").map { name =>
      JobName.fromString(name) match {
        case Left(msg) =>
          msg should include(name)
          msg should include("""must start with "transferJobs/"""")
        case _ => fail
      }
    }
  }

  it should """succeed when the name is prefixed with "transferJobs/"""" ignore ioAssertion {
    randomize("transferJobs/test").map { name =>
      JobName.fromString(name) match {
        case Right(JobName(jn)) => jn shouldBe name
        case _                  => fail
      }
    }
  }

  "OperationName" should """fail when the name is not prefixed with "transferOperations/"""" ignore ioAssertion {
    // Required in Scala 2.12 as another `OperationName` specific to GCE is defined in this package.
    import GoogleStorageTransferService.OperationName
    randomize("test").map { name =>
      OperationName.fromString(name) match {
        case Left(msg) =>
          msg should include(name)
          msg should include("""must start with "transferOperations/"""")
        case _ => fail
      }
    }
  }

  it should """succeed when the name is prefixed with "transferOperations/"""" ignore ioAssertion {
    // Required in Scala 2.12 as another `OperationName` specific to GCE is defined in this package.
    import GoogleStorageTransferService.OperationName
    randomize("transferOperations/test").map { name =>
      OperationName.fromString(name) match {
        case Right(OperationName(on)) => on shouldBe name
        case _                        => fail
      }
    }
  }

  "getStsServiceAccount" should "return a google-owned SA specific to the google project" ignore ioAssertion {
    GoogleStorageTransferService.resource[IO].use { sts =>
      sts.getStsServiceAccount(googleProject) map { case ServiceAccount(_, email, _) =>
        email.value should include("storage-transfer")
        email.value should endWith("gserviceaccount.com")
      }
    }
  }

  implicit class PollTransferOperations(sts: GoogleStorageTransferService[IO]) {
    def await(jobName: JobName): IO[Unit] = IO.sleep(5 seconds).untilM_ {
      sts.listTransferOperations(jobName, googleProject).map {
        case Seq() => false
        case xs    => xs.forall(_.getDone)
      }
    }
  }

  "createTransferJob" should "create a storage transfer service job from one bucket to another" ignore ioAssertion {
    temporaryGcsBucket(googleProject, "workbench-libs-").use { dstBucket =>
      GoogleStorageTransferService.resource[IO].use { sts =>
        for {
          serviceAccount <- sts.getStsServiceAccount(googleProject)
          serviceAccountList = NonEmptyList.one(Identity.serviceAccount(serviceAccount.email.value))

          _ <- (for {
            // STS Service Account requires "Storage Object Viewer" and "Storage Legacy Bucket Reader"
            // roles on the bucket it transfers from
            _ <- storage.setIamPolicy(srcBucket,
                                      Map(
                                        StorageRole.LegacyBucketReader -> serviceAccountList,
                                        StorageRole.ObjectViewer -> serviceAccountList
                                      )
            )
            // STS Service Account requires "Storage Object Creator" and "Storage Legacy Bucket Writer"
            // roles on the bucket it transfers to
            _ <- storage.setIamPolicy(dstBucket,
                                      Map(
                                        StorageRole.LegacyBucketWriter -> serviceAccountList,
                                        StorageRole.ObjectCreator -> serviceAccountList
                                      )
            )
          } yield ()).compile.drain

          jobName <- randomize("transferJobs/workbench-libs-sts-test").flatMap { name =>
            IO.fromEither(JobName.fromString(name).leftMap(errMsg => new WorkbenchException(errMsg)))
          }

          _ <- sts.createTransferJob(
            jobName,
            "testing creating a storage transfer job",
            googleProject,
            srcBucket,
            dstBucket,
            JobTransferSchedule.Immediately
          )

          _ <- sts.await(jobName)

          obj <- storage.getObjectMetadata(dstBucket, GcsBlobName("test_entity.tsv")).compile.lastOrError

        } yield obj should not be NotFound
      }
    }
  }

  it should "delete source objects when so configured" ignore ioAssertion {
    val blobName = GcsBlobName("test.txt")
    val fixtures = for {
      tmpSrcBucket <- temporaryGcsBucket(googleProject, "workbench-libs-")
      _ <- Resource.liftK {
        storage
          .streamUploadBlob(tmpSrcBucket, blobName) {
            fs2.Stream.emits("hello, world!".getBytes)
          }
          .compile
          .drain
      }
      tmpDstBucket <- temporaryGcsBucket(googleProject, "workbench-libs-")
      transferService <- GoogleStorageTransferService.resource[IO]
    } yield (tmpSrcBucket, tmpDstBucket, transferService)

    fixtures.use { case (srcBucket, dstBucket, sts) =>
      for {
        serviceAccount <- sts.getStsServiceAccount(googleProject)
        serviceAccountList = NonEmptyList.one(Identity.serviceAccount(serviceAccount.email.value))

        _ <- (for {
          // STS Service Account requires "Storage Object Admin" and "Storage Legacy Bucket Reader"
          // roles on the bucket it transfers from
          _ <- storage.setIamPolicy(srcBucket,
                                    Map(
                                      StorageRole.LegacyBucketReader -> serviceAccountList,
                                      StorageRole.ObjectAdmin -> serviceAccountList
                                    )
          )
          // STS Service Account requires "Storage Object Creator" and "Storage Legacy Bucket Writer"
          // roles on the bucket it transfers to
          _ <- storage.setIamPolicy(dstBucket,
                                    Map(
                                      StorageRole.LegacyBucketWriter -> serviceAccountList,
                                      StorageRole.ObjectCreator -> serviceAccountList
                                    )
          )
        } yield ()).compile.drain

        jobName <- randomize("transferJobs/workbench-libs-sts-test").flatMap { name =>
          IO.fromEither(JobName.fromString(name).leftMap(errMsg => new WorkbenchException(errMsg)))
        }

        _ <- sts.createTransferJob(
          jobName,
          "testing creating a storage transfer job",
          googleProject,
          srcBucket,
          dstBucket,
          JobTransferSchedule.Immediately,
          options = JobTransferOptions(whenToDelete = DeleteSourceObjectsAfterTransfer).some
        )

        _ <- sts.await(jobName)

        srcObj <- storage.getObjectMetadata(srcBucket, blobName).compile.lastOrError
        dstObj <- storage.getObjectMetadata(dstBucket, blobName).compile.lastOrError
      } yield {
        srcObj shouldBe NotFound
        dstObj should not be NotFound
      }
    }
  }

}
