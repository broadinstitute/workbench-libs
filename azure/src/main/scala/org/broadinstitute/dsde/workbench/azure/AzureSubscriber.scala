package org.broadinstitute.dsde.workbench.azure

import cats.effect.std.{Dispatcher, Queue}
import cats.effect.{Async, Resource, Sync}
import fs2.Stream
import io.circe.Decoder
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger

import java.time.Instant

trait AzureSubscriber[F[_], A] {
  def messages: Stream[F, AzureEvent[A]]
  def start: F[Unit]
  def stop: F[Unit]
}

object AzureSubscriber {
  def resource[F[_]: Async: StructuredLogger, MessageType: Decoder](
    subscriberConfig: AzureServiceBusSubscriberConfig,
    queue: Queue[F, AzureEvent[MessageType]],
  ): Resource[F, AzureSubscriber[F, MessageType]] =
    AzureSubscriberInterpreter.subscriber(
      subscriberConfig,
      queue
    )

  def stringResource[F[_]: Async: StructuredLogger](
    subscriberConfig: AzureServiceBusSubscriberConfig,
    queue: Queue[F, AzureEvent[String]]
  ): Resource[F, AzureSubscriber[F, String]] =
    AzureSubscriberInterpreter.stringSubscriber(subscriberConfig, queue)
}

final case class AzureEvent[A](msg: A, traceId: Option[TraceId], publishedTime: Option[Instant])
final case class AzureServiceBusSubscriberConfig(
  topicName: String,
  subscriptionName: String,
  namespace: Option[String],
  connectionString: Option[String]
)
