package org.broadinstitute.dsde.workbench.google2


import cats.data.NonEmptyList
import cats.effect.std.{Semaphore, UUIDGen}
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import com.google.`type`.Date
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import com.google.cloud.{Identity, Policy}
import org.broadinstitute.dsde.workbench.google2.StorageRole.CustomStorageRole
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject, ServiceAccount}
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._

class GoogleStorageTransferInterpreterSpec extends AsyncFlatSpec with Matchers with WorkbenchTestSuite {

  val db = LocalStorageHelper.getOptions().getService()
  val semaphore = Semaphore[IO](1).unsafeRunSync
  val storage = GoogleStorageInterpreter[IO](db, Some(semaphore))
  val sts = new GoogleStorageTransferInterpreter[IO]()

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

  def temporaryStorageRoles[A](bucket: GcsBucketName,
                               roles: Map[StorageRole, NonEmptyList[Identity]]
                              ): Resource[IO, Unit] = {

    def toRoles(p: Policy): Map[StorageRole, NonEmptyList[Identity]] = Map.from(
      p.getBindings.asScala.map { case (role, identities) =>
        (CustomStorageRole(role.getValue), NonEmptyList.fromList(identities.asScala.toList).get)
      }
    )

    Resource.eval(storage.getIamPolicy(bucket).compile.lastOrError)
      .map(toRoles)
      .flatMap { originalRoles =>
        Resource.make(storage.overrideIamPolicy(bucket, originalRoles ++ roles).compile.drain) { _ =>
          storage.overrideIamPolicy(bucket, originalRoles).compile.drain
        }
      }
  }

  "getStsServiceAccount" should "return a google-owned SA specific to the google project" in ioAssertion {
    sts.getStsServiceAccount(googleProject) map { case ServiceAccount(_, email, _) =>
      email.value should endWith("@storage-transfer-service.iam.gserviceaccount.com")
    }
  }

  "transferBucket" should "start a storage transfer service job from one bucket to another" in ioAssertion {
    temporaryGcsBucket(googleProject, "workspace-libs-").use { dstBucket =>
      sts.getStsServiceAccount(googleProject) flatMap { serviceAccount =>
        val serviceAccountList = NonEmptyList.one(Identity.serviceAccount(serviceAccount.email.value))

        // STS Service Account requires "Storage Object Viewer" and "Storage Legacy Bucket Reader"
        // roles on the bucket it transfers from
        temporaryStorageRoles(srcBucket, Map(
          (StorageRole.LegacyStorageReader, serviceAccountList),
          (StorageRole.ObjectViewer, serviceAccountList)
        )).flatMap { _ =>

          // STS Service Account requires "Storage Object Creator" and "Storage Legacy Bucket Writer"
          // roles on the bucket it transfers to
          temporaryStorageRoles(dstBucket, Map(
            (StorageRole.LegacyStorageWriter, serviceAccountList),
            (StorageRole.ObjectCreator, serviceAccountList)
          ))
        }.use { _ =>
          for {
            jobName <- randomize("workbench-libs-sts-test")
            job <- sts.transferBucket(
              jobName,
              "testing creating a storage transfer job",
              googleProject,
              srcBucket,
              dstBucket,
              Once(Date.newBuilder
                .setYear(2021)
                .setMonth(10)
                .setDay(22)
                .build
              )
            )
          } yield job shouldBe true
        }
      }
    }
  }
}
