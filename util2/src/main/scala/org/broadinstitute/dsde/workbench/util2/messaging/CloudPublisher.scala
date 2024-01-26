package org.broadinstitute.dsde.workbench.util2.messaging

import cats.mtl.Ask
import fs2.Pipe
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.model.TraceId

trait CloudPublisher[F[_]] {
  def publish[MessageType: Encoder]: Pipe[F, MessageType, Unit]
  def publishOne[MessageType: Encoder](message: MessageType, messageAttributes: Option[Map[String, String]] = None)(implicit ev: Ask[F, TraceId]): F[Unit]
  def publishString: Pipe[F, String, Unit]
}
