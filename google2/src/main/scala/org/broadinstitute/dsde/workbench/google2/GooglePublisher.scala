package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Async, ContextShift, Resource, Timer}
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
  def tracedPublish[MessageType: Encoder]: Pipe[F, DecoratedMessage[MessageType], Unit]

  /**
    * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
    */
  def publishString: Pipe[F, String, Unit]

}

object GooglePublisher {
  def resource[F[_]: Async: Timer: ContextShift: StructuredLogger, MessageType: Encoder](config: PublisherConfig): Resource[F, GooglePublisher[F]] = for {
    publisher <- GooglePublisherInterpreter.publisher(config)
  } yield GooglePublisherInterpreter(publisher, config.retryConfig)
}
