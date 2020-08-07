package org.broadinstitute.dsde.workbench.errorReporting

import cats.effect._
import com.google.devtools.clouderrorreporting.v1beta1.SourceLocation

object FakeErrorReporting extends ErrorReporting[IO] {
  override def reportError(msg: String, sourceLocation: SourceLocation): IO[Unit] = IO.unit

  override def reportError(t: Throwable): IO[Unit] = IO.unit
}
