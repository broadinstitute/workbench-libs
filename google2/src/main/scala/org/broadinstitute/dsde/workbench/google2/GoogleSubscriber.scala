package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import io.circe.Decoder

trait GoogleSubscriber[F[_], A] {
  def messages: Stream[F, Event[A]]
  // If you use `start`, make sure to hook up `messages` somewhere as well on the same instance for consuming the messages; Otherwise, messages will be left nacked
  def start: F[Unit]
  def stop: F[Unit]
}

object GoogleSubscriber {
  def resource[F[_]: Effect: Timer: ContextShift: StructuredLogger, MessageType: Decoder](
    subscriberConfig: SubscriberConfig,
    queue: fs2.concurrent.Queue[F, Event[MessageType]]
  ): Resource[F, GoogleSubscriber[F, MessageType]] =
    for {
      subscriberClient <- GoogleSubscriberInterpreter.subscriber(subscriberConfig, queue)
    } yield GoogleSubscriberInterpreter(subscriberClient, queue)

  def stringResource[F[_]: Effect: Timer: ContextShift: StructuredLogger](
    subscriberConfig: SubscriberConfig,
    queue: fs2.concurrent.Queue[F, Event[String]]
  ): Resource[F, GoogleSubscriber[F, String]] =
    for {
      subscriberClient <- GoogleSubscriberInterpreter.stringSubscriber(subscriberConfig, queue)
    } yield GoogleSubscriberInterpreter(subscriberClient, queue)
}
