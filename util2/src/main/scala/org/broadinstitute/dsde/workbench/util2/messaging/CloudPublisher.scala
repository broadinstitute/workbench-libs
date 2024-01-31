package org.broadinstitute.dsde.workbench.util2.messaging

import cats.mtl.Ask
import fs2.Pipe
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.model.TraceId

/***
 * A cloud-agnostic messaging publisher that sends messages to a cloud messaging service.
 */
trait CloudPublisher[F[_]] {

  /***
   * A pipe that sends messages to the cloud messaging service.
   */
  def publish[MessageType: Encoder]: Pipe[F, MessageType, Unit]

  /***
     * Sends a single message to the cloud messaging service.
     */
  def publishOne[MessageType: Encoder](message: MessageType, messageAttributes: Map[String, String] = Map.empty)(
    implicit ev: Ask[F, TraceId]
  ): F[Unit]

  /***
     * A pipe that sends string messages to the cloud messaging service.
     */
  def publishString: Pipe[F, String, Unit]
}
