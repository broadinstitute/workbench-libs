package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import com.google.`type`.Date
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class GoogleStorageTransferInterpreterSpec extends AsyncFlatSpec with Matchers with WorkbenchTestSuite {
  // TODO: Calling service account needs to be storage transfer users in the project to bill
  // TODO: From the bucket it is reading from, STS Service Account needs Storage Object Viewer and Storage Legacy Bucket Reader roles
  // TODO: From the bucket it is reading from, STS Service Account needs Storage Object Creator and Storage Legacy Bucket Writer roles

  "transferBucket" should "start a storage transfer service job from one bucket to another" in ioAssertion {
    val interpreter = new GoogleStorageTransferInterpreter[IO]()

    for {
      job <- interpreter.transferBucket(
        "test-from-intellij",
        "testing creating a transfer job from intellij",
        GoogleProject("general-dev-billing-account"),
        "mob-test-transfer-service-1000tb",
        "mob-test-transfer-service-2",
        Once(Date.newBuilder
          .setYear(2021)
          .setMonth(10)
          .setDay(22)
          .build
        ),
        options = StorageTransferJobOptions(
          whenToOverwrite = OverwriteObjectsAlreadyExistingInSink,
          whenToDelete = NeverDeleteSourceObjects
        ).some
      )
    } yield job shouldBe true
  }
}
