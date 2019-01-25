package org.broadinstitute.dsde.workbench.google2

import cats.implicits._
import cats.effect._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.circe.Decoder

trait GoogleSubscriber[F[_], A] {
  def messages: Stream[F, Event[A]]
  def start: F[Unit]
  def stop: F[Unit]
}

object GoogleSubscriber{
  def resource[F[_]: Effect: Timer: ContextShift: Logger, A: Decoder](
                                                  subscriberConfig: SubscriberConfig,
                                                  queue: fs2.concurrent.Queue[F, Event[A]]
                                                ): Resource[F, GoogleSubscriber[F, A]] = for {
    subscriberClient <- GoogleSubscriberInterpreter.subscriber(subscriberConfig, queue)
  } yield GoogleSubscriberInterpreter(subscriberClient, queue)
}