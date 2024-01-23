package org.broadinstitute.dsde.workbench.azure

import cats.effect._
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits._
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext
import io.circe.Decoder
import io.circe.parser.parse
import org.broadinstitute.dsde.workbench.util2.messaging.{AckHandler, CloudSubscriber, ReceivedMessage}
import org.typelevel.log4cats.StructuredLogger

import scala.util.{Failure, Success, Try}

private[azure] class AzureSubscriberInterpreter[F[_], MessageType](
  clientWrapper: AzureServiceBusReceiverClientWrapper,
  queue: cats.effect.std.Queue[F, ReceivedMessage[MessageType]]
)(implicit F: Async[F])
    extends CloudSubscriber[F, MessageType] {

  override def messages: fs2.Stream[F, ReceivedMessage[MessageType]] = fs2.Stream.fromQueueUnterminated(queue)

  override def start: F[Unit] =
    F.delay(clientWrapper.startProcessor())

  override def stop: F[Unit] =
    F.delay(clientWrapper.stopProcessor())
}

object AzureSubscriberInterpreter {

  def subscriber[F[_], MessageType: Decoder](
    clientWrapper: AzureServiceBusReceiverClientWrapper,
    queue: cats.effect.std.Queue[F, ReceivedMessage[MessageType]]
  )(implicit F: Async[F]): cats.effect.Resource[F, CloudSubscriber[F, MessageType]] = {

    val subscriber = new AzureSubscriberInterpreter[F, MessageType](clientWrapper, queue)

    Resource.make(F.pure(subscriber))(_ => subscriber.stop)
  }

  def subscriber[F[_], MessageType: io.circe.Decoder](
    config: AzureServiceBusSubscriberConfig,
    queue: cats.effect.std.Queue[F, ReceivedMessage[MessageType]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, CloudSubscriber[F, MessageType]] =
    for {
      dispatcher <- Dispatcher.sequential[F]

      messageHandler =
        AzureReceivedMessageHandlerInterpreter[F, MessageType](AzureReceivedMessageDecoder.jsonDecoder[MessageType],
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
    queue: cats.effect.std.Queue[F, ReceivedMessage[String]]
  )(implicit F: Async[F]): cats.effect.Resource[F, CloudSubscriber[F, String]] = {

    val subscriber = new AzureSubscriberInterpreter[F, String](clientWrapper, queue)

    Resource.make(F.pure(subscriber))(_ => subscriber.stop)
  }

  def stringSubscriber[F[_]](
    config: AzureServiceBusSubscriberConfig,
    queue: cats.effect.std.Queue[F, ReceivedMessage[String]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, CloudSubscriber[F, String]] =
    for {
      dispatcher <- Dispatcher.sequential[F]

      messageHandler =
        AzureReceivedMessageHandlerInterpreter[F, String](AzureReceivedMessageDecoder.stringDecoder, queue, dispatcher)

      clientWrapper = AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper[F, String](config,
                                                                                                  messageHandler
      )

      subscriberInterp = new AzureSubscriberInterpreter(clientWrapper, queue)

      sub <- Resource.make(F.pure(subscriberInterp))(_ => subscriberInterp.stop)
    } yield sub
}

trait AzureReceivedMessageDecoder[MessageType] {
  def decodeMessage(messageContext: ServiceBusReceivedMessageContext): Either[Error, ReceivedMessage[MessageType]]
}

object AzureReceivedMessageDecoder {
  def jsonDecoder[MessageType: Decoder]: AzureReceivedMessageDecoder[MessageType] = new JsonMessageDecoder[MessageType]

  def stringDecoder: AzureReceivedMessageDecoder[String] = new StringMessageDecoder
}

final class JsonMessageDecoder[MessageType: Decoder] extends AzureReceivedMessageDecoder[MessageType] {

  override def decodeMessage(
    messageContext: ServiceBusReceivedMessageContext
  ): Either[Error, ReceivedMessage[MessageType]] = {

    val message = messageContext.getMessage
    val jsonResult = parse(message.getBody.toString)

    jsonResult match {
      case Right(json) =>
        Decoder[MessageType].decodeJson(json) match {
          case Right(decodedData) =>
            val timestamp = ServiceBusMessageUtils.getEnqueuedTimeOrDefault(message)
            val traceId = ServiceBusMessageUtils.getTraceIdFromCorrelationId(message)
            val ackHandler = AzureAckHandlerInterpreter.createAckHandler(messageContext)
            Right(ReceivedMessage(decodedData, traceId, timestamp, ackHandler))
          case Left(err) =>
            Left(new Error(err.message))
        }
      case Left(err) =>
        Left(new Error(err.message))
    }
  }
}

final class StringMessageDecoder extends AzureReceivedMessageDecoder[String] {
  override def decodeMessage(messageContext: ServiceBusReceivedMessageContext): Either[Error, ReceivedMessage[String]] =
    try {
      val message = messageContext.getMessage
      Right {
        val timestamp = ServiceBusMessageUtils.getEnqueuedTimeOrDefault(message)
        val traceId = ServiceBusMessageUtils.getTraceIdFromCorrelationId(message)
        val ackHandler = AzureAckHandlerInterpreter.createAckHandler(messageContext)

        ReceivedMessage(message.getBody.toString, traceId, timestamp, ackHandler)
      }
    } catch {
      case e: Exception => Left(new Error(e.getMessage))
    }
}
trait AzureReceivedMessageHandler {
  def handleMessage(messageContext: ServiceBusReceivedMessageContext): Try[Unit]
}

final private class AzureReceivedMessageHandlerInterpreter[F[_], MessageType: Decoder](
  decoder: AzureReceivedMessageDecoder[MessageType],
  queue: Queue[F, ReceivedMessage[MessageType]],
  dispatcher: Dispatcher[F]
)(implicit F: Async[F], logger: StructuredLogger[F])
    extends AzureReceivedMessageHandler {

  override def handleMessage(messageContext: ServiceBusReceivedMessageContext): Try[Unit] =
    try {
      val message = messageContext.getMessage
      val loggingContext = Map("traceId" -> Option(message.getCorrelationId).getOrElse("None"))
      decoder.decodeMessage(messageContext) match {
        case Right(value) =>
          val enqueue = for {
            _ <- logger.info(loggingContext)(
              s"Subscriber Successfully decoded message, with message ID: ${message.getMessageId}."
            )
            _ <- queue.offer(value)
          } yield ()
          dispatcher.unsafeRunSync(enqueue)
          Success()
        case Left(exception) =>
          val handleError = for {
            _ <- logger.error(loggingContext)(
              s"Subscriber failed to process message with ID: ${message.getMessageId} due to ${exception.getMessage}"
            )
          } yield ()
          dispatcher.unsafeRunSync(handleError)
          Failure(exception)
      }
    } catch {
      case exception: Throwable =>
        messageContext.abandon()
        Failure(exception)
    }
}

object AzureReceivedMessageHandlerInterpreter {
  def apply[F[_]: Async, MessageType: Decoder](
    decoder: AzureReceivedMessageDecoder[MessageType],
    queue: Queue[F, ReceivedMessage[MessageType]],
    dispatcher: Dispatcher[F]
  )(implicit logger: StructuredLogger[F]): AzureReceivedMessageHandler =
    new AzureReceivedMessageHandlerInterpreter[F, MessageType](decoder, queue, dispatcher)
}

final private class AzureAckHandlerInterpreter(messageContext: ServiceBusReceivedMessageContext) extends AckHandler {
  override def ack(): Unit = messageContext.complete()

  override def nack(): Unit = messageContext.abandon()
}

object AzureAckHandlerInterpreter {
  def createAckHandler(messageContext: ServiceBusReceivedMessageContext): AckHandler = new AzureAckHandlerInterpreter(
    messageContext
  )
}

final case class AzureServiceBusSubscriberConfig(
  topicName: String,
  subscriptionName: String,
  // namespace is required if Managed Identity is used
  namespace: Option[String],
  // if then connection string is not provided, assume that Managed Identity is used
  connectionString: Option[String],
  maxConcurrentCalls: Int = 1,
  prefetchCount: Int = 1
) {}
