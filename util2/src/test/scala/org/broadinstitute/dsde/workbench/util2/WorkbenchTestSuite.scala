package org.broadinstitute.dsde.workbench.util2

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.Assertion
import org.scalatest.prop.Configuration
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.global

trait WorkbenchTestSuite {
  implicit val timer: Timer[IO] = IO.timer(global)
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val logger = new ConsoleLogger("unit_test", LogLevel(false, false, false, true))

  def ioAssertion(test: => IO[Assertion]): Future[Assertion] = test.unsafeToFuture()
}

trait PropertyBasedTesting extends ScalaCheckPropertyChecks with Configuration {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(
    minSuccessful = 3
  )
}
