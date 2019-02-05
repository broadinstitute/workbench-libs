package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Async, ContextShift, Resource, Timer}
import fs2.Sink
import io.circe.Encoder

trait GooglePublisher[F[_]] {
  /**
    * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
    */
  def publish[MessageType: Encoder]: Sink[F, MessageType]

  /**
    * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
    */
  def publishString: Sink[F, String]
}

object GooglePublisher {
  def resource[F[_]: Async: Timer: ContextShift, MessageType: Encoder](config: PublisherConfig): Resource[F, GooglePublisher[F]] = for {
    publisher <- GooglePublisherInterpreter.publisher(config)
  } yield GooglePublisherInterpreter(publisher, config.retryConfig)
}
