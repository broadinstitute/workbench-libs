package org.broadinstitute.dsde.workbench.azure

import cats.effect.{Async, Resource}
import cats.implicits.toFlatMapOps
import cats.mtl.Ask
import cats.syntax.all._
import com.azure.messaging.servicebus.ServiceBusMessage
import fs2.{Pipe, Stream}
import io.circe.Encoder
import io.circe.syntax._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.withLogging
import org.typelevel.log4cats.StructuredLogger

private[azure] class AzurePublisherInterpreter[F[_]: Async: StructuredLogger](
  clientWrapper: AzureServiceBusSenderClientWrapper
) extends AzurePublisher[F] {

  override def publish[MessageType: Encoder]: Pipe[F, MessageType, Unit] = in =>
    in.flatMap { message =>
      Stream
        .eval(publishMessage(message.asJson.noSpaces, None))
    }

  override def publishNative: Pipe[F, ServiceBusMessage, Unit] = in =>
    in.flatMap(s => Stream.eval(publishServiceBusMessage(s)))

  override def publishNativeOne(message: ServiceBusMessage): F[Unit] = publishServiceBusMessage(message)

  override def publishOne[MessageType: Encoder](message: MessageType)(implicit ev: Ask[F, TraceId]): F[Unit] =
    for {
      traceId <- ev.ask
      _ <- publishMessage(message.asJson.noSpaces, Some(traceId))
    } yield ()

  override def publishString: Pipe[F, String, Unit] = in => in.flatMap(s => Stream.eval(publishMessage(s, None)))

  private def publishMessage(message: String, traceId: Option[TraceId]): F[Unit] =
    publishServiceBusMessage(message, traceId)

  private def publishServiceBusMessage(messageBody: String, traceId: Option[TraceId]): F[Unit] = {
    val message = new ServiceBusMessage(messageBody)
    traceId.foreach(id => message.setCorrelationId(id.asString))

    publishServiceBusMessage(message)
  }
  private def publishServiceBusMessage(message: ServiceBusMessage): F[Unit] =
    withLogging(
      Async[F].async[Unit] { cb =>
        Async[F]
          .delay(
            clientWrapper
              .sendMessageAsync(message)
              .doOnSuccess(_ => cb(Right(())))
              .doOnError(e => cb(Left(e)))
              .block(AzureServiceBusPublisherConfig.defaultTimeout)
          )
          .as(None)
      }.void,
      Option(message.getCorrelationId).map(s => TraceId(s)),
      s"Publishing message to Service Bus",
      actionName = "azureServiceBusCall"
    )
}

object AzurePublisherInterpreter {
  def publisher[F[_]: Async: StructuredLogger](
    clientWrapper: AzureServiceBusSenderClientWrapper
  ): Resource[F, AzurePublisher[F]] =
    for {
      resourceSenderClient <- Resource.make(
        Async[F].delay {
          clientWrapper
        }
      )(c => Async[F].delay(c.close()))
    } yield new AzurePublisherInterpreter[F](resourceSenderClient)

  def publisher[F[_]: Async: StructuredLogger](config: AzureServiceBusPublisherConfig): Resource[F, AzurePublisher[F]] =
    publisher(AzureServiceBusSenderClientWrapper.createSenderClientWrapper(config))
}
