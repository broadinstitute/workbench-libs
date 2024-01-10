package org.broadinstitute.dsde.workbench.azure

import cats.effect.{Async, Resource}

import cats.mtl.Ask
import com.azure.messaging.servicebus.ServiceBusMessage
import fs2.Pipe
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger

trait AzurePublisher[F[_]] {

    def publish[MessageType: Encoder]: Pipe[F, MessageType, Unit]

    def publishNative: Pipe[F, ServiceBusMessage, Unit]

    def publishNativeOne(message: ServiceBusMessage): F[Unit]

    def publishOne[MessageType: Encoder](message: MessageType)(implicit ev: Ask[F, TraceId]): F[Unit]

    def publishString: Pipe[F, String, Unit]

}

object AzurePublisher {
  def resource[F[_]: Async: StructuredLogger](
                                               clientWrapper: AzureServiceBusSenderClientWrapper
                                             ): Resource[F, AzurePublisher[F]] =
    AzurePublisherInterpreter.publisher(clientWrapper)
}

final case class AzureServiceBusPublisherConfig(topicName: String, connectionString: Option[String])
