package org.broadinstitute.dsde.workbench.util2.messaging

import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId

import java.time.Instant

/***
 * A cloud-agnostic messaging subscriber that receives messages from a cloud messaging service.
 */
trait CloudSubscriber[F[_], MessageType] {

  /***
   * A stream of messages received from the cloud messaging service.
   */
  def messages: Stream[F, ReceivedMessage[MessageType]]

  /***
   * Starts the subscriber background process.
   */
  def start: F[Unit]

  /***
     * Stops the subscriber background process.
     */
  def stop: F[Unit]
}

/***
 * A message received from a cloud messaging service.
 * Contains the message itself, along with metadata about the message, and the ability to ack/nack the message via the ackHandler.
 */
final case class ReceivedMessage[A](msg: A, traceId: Option[TraceId], publishedTime: Instant, ackHandler: AckHandler)
