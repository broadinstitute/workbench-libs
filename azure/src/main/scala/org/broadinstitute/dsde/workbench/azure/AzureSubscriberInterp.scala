package org.broadinstitute.dsde.workbench.azure

import cats.effect._
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits._
import com.azure.messaging.servicebus.ServiceBusReceivedMessage
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
    try
      Right {
        val timestamp = ServiceBusMessageUtils.getEnqueuedTimeOrDefault(message)
        val traceId = ServiceBusMessageUtils.getTraceIdFromCorrelationId(message)

        AzureEvent(message.getBody.toString, traceId, timestamp)
      }
    catch {
      case e: Exception => Left(new Error(e.getMessage))
    }
}
trait AzureEventMessageHandler {
  def handleMessage(message: ServiceBusReceivedMessage): Try[Unit]
}

final private class AzureEventMessageHandlerInterpreter[F[_], MessageType: Decoder](
  decoder: AzureEventMessageDecoder[MessageType],
  queue: Queue[F, AzureEvent[MessageType]],
  dispatcher: Dispatcher[F]
)(implicit F: Async[F], logger: StructuredLogger[F])
    extends AzureEventMessageHandler {

  override def handleMessage(message: ServiceBusReceivedMessage): Try[Unit] = {
    val loggingContext = Map("traceId" -> Option(message.getCorrelationId).getOrElse("None"))
    decoder.decodeMessage(message) match {
      case Right(value) =>
        val enqueue = for {
          _ <- logger.info(loggingContext)(s"Subscriber Successfully decoded the $message.")
          _ <- queue.offer(value)
        } yield ()
        dispatcher.unsafeRunSync(enqueue)
        Success()
      case Left(exception) =>
        val handleError = for {
          _ <- logger.error(loggingContext)(s"Subscriber failed to process $message due to ${exception.getMessage}")
        } yield ()
        dispatcher.unsafeRunSync(handleError)
        Failure(exception)
    }
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
