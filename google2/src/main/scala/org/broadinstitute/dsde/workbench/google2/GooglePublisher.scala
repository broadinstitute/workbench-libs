package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Async, ContextShift, Resource, Timer}
import com.google.pubsub.v1.PubsubMessage
import fs2.Pipe
import io.chrisdavenport.log4cats.StructuredLogger
import io.circe.Encoder

trait GooglePublisher[F[_]] {

  /**
   * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
   */
  def publish[MessageType: Encoder]: Pipe[F, MessageType, Unit]

  /**
   * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
   */
  def publishNative: Pipe[F, PubsubMessage, Unit]

  /**
   * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
   */
  def publishString: Pipe[F, String, Unit]

}

object GooglePublisher {
  def resource[F[_]: Async: Timer: ContextShift: StructuredLogger](
    config: PublisherConfig
  ): Resource[F, GooglePublisher[F]] =
    for {
      publisher <- GooglePublisherInterpreter.publisher(config)
    } yield GooglePublisherInterpreter(publisher, config.retryConfig)
}
