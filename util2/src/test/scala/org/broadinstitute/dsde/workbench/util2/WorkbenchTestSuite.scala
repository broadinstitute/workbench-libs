package org.broadinstitute.dsde.workbench.util2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.Assertion
import org.scalatest.prop.Configuration
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.Future

trait WorkbenchTestSuite {
  implicit val logger: ConsoleLogger = new ConsoleLogger("unit_test", LogLevel(false, false, true, true))

  def ioAssertion(test: => IO[Assertion]): Future[Assertion] = test.unsafeToFuture()
}

trait PropertyBasedTesting extends ScalaCheckPropertyChecks with Configuration {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(
    minSuccessful = 3
  )
}
