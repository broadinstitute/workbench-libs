package org.broadinstitute.dsde.workbench.google2

import fs2.Sink
import io.circe.Encoder

trait GooglePublisher[F[_]] {
  def publish[A: Encoder]: Sink[F, A]
}
