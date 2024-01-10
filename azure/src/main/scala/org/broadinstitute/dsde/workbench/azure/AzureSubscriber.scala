package org.broadinstitute.dsde.workbench.azure

import cats.effect.{Async, Resource}
import cats.effect.std.Queue
import fs2.Stream
import io.circe.Decoder
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger
import com.google.protobuf.Timestamp

trait AzureSubscriber[F[_], A] {
  def messages: Stream[F, AzureEvent[A]]
  def start: F[Unit]
  def stop: F[Unit]
}

object AzureSubscriber {
 def resource[F[_]: Async: StructuredLogger, MessageType: Decoder](
    subscriberConfig: AzureServiceBusSubscriberConfig,
    queue: Queue[F, AzureEvent[MessageType]]
  ): Resource[F, AzureSubscriber[F, MessageType]] =
    AzureSubscriberInterpreter.subscriber(AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper(subscriberConfig), queue)


 def stringResource[F[_]: Async: StructuredLogger](
    subscriberConfig: AzureServiceBusSubscriberConfig,
    queue: Queue[F, AzureEvent[String]]
  ): Resource[F, AzureSubscriber[F, String]] =
    AzureSubscriberInterpreter.stringSubscriber(subscriberConfig, queue)
}

//using google protobuf Timestamp for consistency
final case class AzureEvent[A](msg: A, traceId: Option[TraceId], publishedTime: Timestamp)
final case class AzureServiceBusSubscriberConfig(
                                                  topicName: String,
                                                  subscriptionName: String,
                                                  namespace: Option[String],
                                                  connectionString: Option[String]
                                                )