package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import org.broadinstitute.dsde.workbench.DoneCheckable
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import org.broadinstitute.dsde.workbench.google2.PackageSpec.{neverDoneCheckable, CountingException}

final class PackageSpec extends AnyFlatSpecLike with Matchers with WorkbenchTestSuite {
  "streamFUntilDone" should "not fail even if DoneCheckable condition is not satisfied" in {
    var count = 0

    def incrUntilThenThrow(max: Int): IO[Either[CountingException, Int]] =
      IO {
        if (count < max) {
          count = count + 1
          Right(count)
        } else Left(CountingException(s"Can't increment to more than ${max}"))
      }

    val res = streamFUntilDone(incrUntilThenThrow(3), maxAttempts = 5, delay = 1 milliseconds).compile.lastOrError
      .unsafeRunSync()

    res shouldBe Left(CountingException(s"Can't increment to more than 3"))
    count shouldBe 3
  }
}

object PackageSpec {
  implicit val neverDoneCheckable: DoneCheckable[Either[CountingException, Int]] = _ => false

  final case class CountingException(message: String = null, cause: Throwable = null) extends Exception(message, cause)
}
