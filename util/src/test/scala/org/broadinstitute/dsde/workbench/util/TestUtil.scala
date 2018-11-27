package org.broadinstitute.dsde.workbench.util

import cats.effect.IO
import org.scalatest.Assertion

import scala.concurrent.Future

object TestUtil {
  def ioAssertion(test: => IO[Assertion]): Future[Assertion] = test.unsafeToFuture()
}
