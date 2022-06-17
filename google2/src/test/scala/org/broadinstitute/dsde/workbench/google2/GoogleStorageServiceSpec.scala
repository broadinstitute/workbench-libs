package org.broadinstitute.dsde.workbench.google2

import cats.data.NonEmptyList
import cats.effect.IO
import com.google.cloud.storage.Storage.BucketSourceOption
import com.google.cloud.{Identity, Policy, Role}
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.mock.BaseFakeGoogleStorage
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.convert.ImplicitConversions._

class GoogleStorageServiceSpec extends AsyncFlatSpec with Matchers with WorkbenchTestSuite {

  "removeIamPolicy" should "remove the specified roles from the iam policy" in ioAssertion {
    val idToRemove = Identity.group("kgb@mail.ru")
    val idsToKeep = List(
      Identity.user("simply.sausages@hotmail.com"),
      Identity.user("francois-sauvage@fancymail.com"),
      Identity.user("pierre.beauvais@gmail.com")
    )

    val storageService = new BaseFakeGoogleStorage {
      override def getIamPolicy(bucketName: GcsBucketName,
                                traceId: Option[TraceId],
                                retryConfig: RetryConfig,
                                bucketSourceOptions: List[BucketSourceOption]
      ): fs2.Stream[IO, Policy] =
        fs2.Stream.emit {
          Policy.newBuilder
            .addIdentity(Role.of(StorageRole.ObjectAdmin.name), idsToKeep.head)
            .addIdentity(Role.of(StorageRole.ObjectAdmin.name), idToRemove, idsToKeep.tail: _*)
            .build
        }

      override def setIamPolicy(bucketName: GcsBucketName,
                                roles: Map[StorageRole, NonEmptyList[Identity]],
                                traceId: Option[TraceId],
                                retryConfig: RetryConfig,
                                bucketSourceOptions: List[BucketSourceOption]
      ): fs2.Stream[IO, Unit] =
        fs2.Stream.eval {
          val objectAdmins = roles(StorageRole.ObjectAdmin).toList
          if (objectAdmins.contains(idToRemove))
            IO.raiseError(new AssertionError(s"The id-to-remove was not removed from the iam policy"))
          else if (!objectAdmins.containsAll(idsToKeep))
            IO.raiseError(new AssertionError(s"The ids-to-keep were missing from the iam policy"))
          else
            IO.unit
        }
    }

    for {
      _ <- storageService
        .removeIamPolicy(
          GcsBucketName("test-bucket-name"),
          Map(StorageRole.ObjectAdmin -> NonEmptyList.one(idToRemove))
        )
        .compile
        .drain
    } yield succeed
  }

}
