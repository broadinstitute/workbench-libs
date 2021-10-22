package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import com.google.`type`.Date
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class GoogleStorageTransferInterpreterSpec extends AsyncFlatSpec with Matchers with WorkbenchTestSuite {
  "transferBucket" should "start a storage transfer service job from one bucket to another" in ioAssertion {
    val interpreter = new GoogleStorageTransferInterpreter[IO]()

    for {
      job <- interpreter.transferBucket(
        "test-trom-intellij",
        "testing creating a transfer job from intellij",
        GoogleProject("broad-dsde-dev"),
        "mob-test-transfer-service",
        "mob-test-transfer-service-2",
        Once(Date.newBuilder
          .setYear(2021)
          .setMonth(10)
          .setDay(21)
          .build
        )
      )
    } yield job shouldBe true
  }
}