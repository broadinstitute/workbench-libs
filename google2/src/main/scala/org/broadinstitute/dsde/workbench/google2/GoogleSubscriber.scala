package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.std.Queue
import io.circe.Decoder
import org.broadinstitute.dsde.workbench.util2.messaging.{CloudSubscriber, ReceivedMessage}
import org.typelevel.log4cats.StructuredLogger

object GoogleSubscriber {
  def resource[F[_]: Async: StructuredLogger, MessageType: Decoder](
    subscriberConfig: SubscriberConfig,
    queue: Queue[F, ReceivedMessage[MessageType]]
  ): Resource[F, CloudSubscriber[F, MessageType]] =
    for {
      subscriberClient <- GoogleSubscriberInterpreter.subscriber(subscriberConfig, queue)
    } yield GoogleSubscriberInterpreter(subscriberClient, queue)

  def stringResource[F[_]: Async: StructuredLogger](
    subscriberConfig: SubscriberConfig,
    queue: Queue[F, ReceivedMessage[String]]
  ): Resource[F, CloudSubscriber[F, String]] =
    for {
      subscriberClient <- GoogleSubscriberInterpreter.stringSubscriber(subscriberConfig, queue)
    } yield GoogleSubscriberInterpreter(subscriberClient, queue)
}
