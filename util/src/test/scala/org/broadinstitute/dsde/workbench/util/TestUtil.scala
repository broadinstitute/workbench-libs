package org.broadinstitute.dsde.workbench.util

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.Assertion
import org.scalatest.prop.{Configuration, PropertyChecks}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.global

trait WorkbenchTest {
  implicit val timer: Timer[IO] = IO.timer(global)
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def ioAssertion(test: => IO[Assertion]): Future[Assertion] = test.unsafeToFuture()
}

trait PropertyBasedTesting extends PropertyChecks with Configuration {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 3)
}