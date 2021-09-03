package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.broadinstitute.dsde.workbench.{DoneCheckable, StreamTimeoutError}
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

final class PackageSpec extends AnyFlatSpecLike with Matchers with WorkbenchTestSuite {
  "streamUntilDoneOrTimeout" should "raise error if DoneCheckable condition is not met after maxAttempts" in {
    var count: Int = 0

    implicit val doneCountingTo5: DoneCheckable[Int] = _ => count == 5

    def incrUpTo(done: Int): IO[Int] = IO {
      if (count < done) count = count + 1
      count
    }

    val resultWhenReachingMaxAttemptsBeforeDone =
      streamUntilDoneOrTimeout(incrUpTo(5), 2, 1 milliseconds, "should have timeout error")
    intercept[StreamTimeoutError] {
      resultWhenReachingMaxAttemptsBeforeDone.unsafeRunSync()
    }
    count shouldBe 2

    val resultWhenReachingDoneBeforeMaxAttempts =
      streamUntilDoneOrTimeout(incrUpTo(5), 4, 1 milliseconds, "shouldn't have timeout error")
    resultWhenReachingDoneBeforeMaxAttempts.unsafeRunSync() shouldBe 5
    count shouldBe 5
  }
}
