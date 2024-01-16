package org.broadinstitute.dsde.workbench.azure

import cats.effect._
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits._
import com.azure.messaging.servicebus.{
  ServiceBusErrorContext,
  ServiceBusReceivedMessage,
  ServiceBusReceivedMessageContext
}
import io.circe.Decoder
import io.circe.parser.parse
import org.typelevel.log4cats.StructuredLogger

import scala.util.{Failure, Success, Try}

private[azure] class AzureSubscriberInterpreter[F[_], MessageType](
  clientWrapper: AzureServiceBusReceiverClientWrapper,
  queue: cats.effect.std.Queue[F, AzureEvent[MessageType]]
)(implicit F: Async[F], logger: StructuredLogger[F])
    extends AzureSubscriber[F, MessageType] {

  override def messages: fs2.Stream[F, AzureEvent[MessageType]] = fs2.Stream.fromQueueUnterminated(queue)

  override def start: F[Unit] =
    F.delay(clientWrapper.startProcessor())

  override def stop: F[Unit] =
    F.delay(clientWrapper.stopProcessor())
}

object AzureSubscriberInterpreter {

  def subscriber[F[_], MessageType: Decoder](
    clientWrapper: AzureServiceBusReceiverClientWrapper,
    queue: cats.effect.std.Queue[F, AzureEvent[MessageType]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, MessageType]] = {

    val subscriber = new AzureSubscriberInterpreter[F, MessageType](clientWrapper, queue)

    Resource.make(F.pure(subscriber))(_ => subscriber.stop)
  }

  def subscriber[F[_], MessageType: io.circe.Decoder](
    config: AzureServiceBusSubscriberConfig,
    queue: cats.effect.std.Queue[F, AzureEvent[MessageType]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, MessageType]] =
    for {
      dispatcher <- Dispatcher.sequential[F]

      messageHandler =
        AzureEventMessageHandlerInterpreter[F, MessageType](AzureEventMessageDecoder.jsonDecoder[MessageType],
                                                            queue,
                                                            dispatcher
        )

      clientWrapper = AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper[F, MessageType](config,
                                                                                                       messageHandler
      )

      subscriberInterp = new AzureSubscriberInterpreter(clientWrapper, queue)

      sub <- Resource.make(F.pure(subscriberInterp))(_ => subscriberInterp.stop)
    } yield sub

  def stringSubscriber[F[_]](
    clientWrapper: AzureServiceBusReceiverClientWrapper,
    queue: cats.effect.std.Queue[F, AzureEvent[String]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, String]] = {

    val subscriber = new AzureSubscriberInterpreter[F, String](clientWrapper, queue)

    Resource.make(F.pure(subscriber))(_ => subscriber.stop)
  }

  def stringSubscriber[F[_]](
    config: AzureServiceBusSubscriberConfig,
    queue: cats.effect.std.Queue[F, AzureEvent[String]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, String]] =
    for {
      dispatcher <- Dispatcher.sequential[F]

      messageHandler =
        AzureEventMessageHandlerInterpreter[F, String](AzureEventMessageDecoder.stringDecoder, queue, dispatcher)

      clientWrapper = AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper[F, String](config,
                                                                                                  messageHandler
      )

      subscriberInterp = new AzureSubscriberInterpreter(clientWrapper, queue)

      sub <- Resource.make(F.pure(subscriberInterp))(_ => subscriberInterp.stop)
    } yield sub
}

trait AzureEventMessageDecoder[MessageType] {
  def decodeMessage(message: ServiceBusReceivedMessage): Either[Error, AzureEvent[MessageType]]
}

object AzureEventMessageDecoder {
  def jsonDecoder[MessageType: Decoder]: AzureEventMessageDecoder[MessageType] = new JsonMessageDecoder[MessageType]

  def stringDecoder: AzureEventMessageDecoder[String] = new StringMessageDecoder
}

final class JsonMessageDecoder[MessageType: Decoder] extends AzureEventMessageDecoder[MessageType] {

  override def decodeMessage(message: ServiceBusReceivedMessage): Either[Error, AzureEvent[MessageType]] = {

    val jsonResult = parse(message.getBody.toString)

    jsonResult match {
      case Right(json) =>
        Decoder[MessageType].decodeJson(json) match {
          case Right(decodedData) =>
            val timestamp = ServiceBusMessageUtils.getEnqueuedTimeOrDefault(message)
            val traceId = ServiceBusMessageUtils.getTraceIdFromCorrelationId(message)
            Right(AzureEvent(decodedData, traceId, timestamp))
          case Left(err) =>
            Left(new Error(err.message))
        }
      case Left(err) =>
        Left(new Error(err.message))
    }
  }
}

final class StringMessageDecoder extends AzureEventMessageDecoder[String] {
  override def decodeMessage(message: ServiceBusReceivedMessage): Either[Error, AzureEvent[String]] =
    Either
      .catchNonFatal {
        val timestamp = ServiceBusMessageUtils.getEnqueuedTimeOrDefault(message)
        val traceId = ServiceBusMessageUtils.getTraceIdFromCorrelationId(message)

        AzureEvent(message.getBody.toString, traceId, timestamp)
      }
      .leftMap(t => new Error(t.getMessage))
}
trait AzureEventMessageHandler {
  def handleMessage(message: ServiceBusReceivedMessageContext): Unit
  def handleError(context: ServiceBusErrorContext): Unit
}

final private class AzureEventMessageHandlerInterpreter[F[_], MessageType: Decoder](
  decoder: AzureEventMessageDecoder[MessageType],
  queue: Queue[F, AzureEvent[MessageType]],
  dispatcher: Dispatcher[F]
)(implicit F: Async[F], logger: StructuredLogger[F])
    extends AzureEventMessageHandler {

  override def handleMessage(context: ServiceBusReceivedMessageContext): Unit = {
    val message = context.getMessage
    val loggingContext = Map("traceId" -> Option(message.getCorrelationId).getOrElse("None"))
    decoder.decodeMessage(message) match {
      case Right(value) =>
        val enqueue = for {
          _ <- logger.info(loggingContext)(s"Subscriber Successfully decoded the $message.")
          _ <- queue.offer(value)
          _ <- F.delay(context.complete())
        } yield ()
        dispatcher.unsafeRunSync(enqueue)
      case Left(exception) =>
        val handleError = for {
          _ <- logger.error(loggingContext, exception)(
            s"Subscriber failed to decode $message due to ${exception.getMessage}"
          )
          _ <- F.delay(context.abandon())
        } yield ()
        dispatcher.unsafeRunSync(handleError)
    }
  }

  override def handleError(context: ServiceBusErrorContext): Unit = {
    val res = logger.error(context.getException)(
      s"Subscriber ran into error receiving messages due to ${context.getException.getMessage}"
    )
    dispatcher.unsafeRunSync(res)
  }
}

object AzureEventMessageHandlerInterpreter {
  def apply[F[_]: Async, MessageType: Decoder](
    decoder: AzureEventMessageDecoder[MessageType],
    queue: Queue[F, AzureEvent[MessageType]],
    dispatcher: Dispatcher[F]
  )(implicit logger: StructuredLogger[F]): AzureEventMessageHandler =
    new AzureEventMessageHandlerInterpreter[F, MessageType](decoder, queue, dispatcher)
}
