package org.broadinstitute.dsde.workbench.util2.messaging

import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId

import java.time.Instant

trait CloudSubscriber[F[_], MessageType] {
  def messages: Stream[F, ReceivedMessage[MessageType]]
  def start: F[Unit]
  def stop: F[Unit]
}

final case class ReceivedMessage[A](msg: A,
                                    traceId: Option[TraceId],
                                    publishedTime: Option[Instant],
                                    ackHandler: AckHandler
)
