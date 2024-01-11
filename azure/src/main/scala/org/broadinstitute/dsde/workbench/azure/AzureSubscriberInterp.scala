package org.broadinstitute.dsde.workbench.azure

import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.azure.messaging.servicebus.ServiceBusReceivedMessage
import com.google.protobuf.Timestamp
import io.circe.Decoder
import io.circe.parser.parse
import org.typelevel.log4cats.StructuredLogger
import reactor.core.Disposable

private[azure] class AzureSubscriberInterpreter[F[_], MessageType](
  clientWrapper: AzureServiceBusReceiverClientWrapper,
  queue: cats.effect.std.Queue[F, AzureEvent[MessageType]],
  messageHandler: MessageHandler[MessageType]
)(implicit F: Async[F], logger: StructuredLogger[F])
    extends AzureSubscriber[F, MessageType] {
  private var subscription: Option[Disposable] = None

  override def messages: fs2.Stream[F, AzureEvent[MessageType]] = fs2.Stream.fromQueueUnterminated(queue)

  override def start: F[Unit] = F.async[Unit] { callback =>
    F.delay {
       clientWrapper
        .receiveMessagesAsync()
        .subscribe(
            (message: ServiceBusReceivedMessage) => {
            val loggingContext = Map("traceId" -> Option(message.getCorrelationId).getOrElse("None"))

            messageHandler.handleMessage(message) match {
              case Right(value) =>
                 for {
                  _ <- logger.info(loggingContext)(s"Subscriber Successfully received $message.")
                  _ <- queue.offer(value)
                  _ <- F.delay(clientWrapper.complete(message))
                  _ <- F.delay(callback(Right(())))
                } yield ()

//                logger.info(loggingContext)(s"Subscriber Successfully received $message.")
//                IO(queue.offer(value)).unsafeRunAsync(_ => ())

//                try
//                  for {
//                    r <- IO(queue.offer(value))
//                    _ <- r match {
//                      case Left(e) =>
//                        logger.info(loggingContext)(s"Subscriber fail to enqueue $message due to $e")
//                      case Right(_) =>
//                        clientWrapper.complete(message)
//                        callback(Right(()))
//                        logger.info(loggingContext)(s"Subscriber Successfully received $message.")
//                    }
//                  } yield ()
//                catch {
//                  case e: Exception =>
//                    logger.error(loggingContext)(s"Subscriber failed to complete $message due to ${e.getMessage}")
//                }
              case Left(exception) =>
                logger.error(loggingContext)(s"Subscriber failed to process $message due to ${exception.getMessage}")
                clientWrapper.abandon(message)
                callback(Left(exception))
            }
          },
          (failure: Throwable) => {
            logger.error(s"Error in message subscription: ${failure.getMessage}")
            callback(Left(failure))
          }
        )
     // subscription = Some(disposable)
    }.as(None)
  }

  override def stop: F[Unit] = F.delay {
    // Stop processor and dispose when done processing messages.
    clientWrapper.close()

    if (subscription.isDefined) {
      subscription.get.dispose()
    }
  }
}

object AzureSubscriberInterpreter {

  def subscriber[F[_], MessageType: io.circe.Decoder](
    clientWrapper: AzureServiceBusReceiverClientWrapper,
    queue: cats.effect.std.Queue[F, AzureEvent[MessageType]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, MessageType]] = {

    val messageHandler = new JsonDecoderMessageHandler[MessageType]

    val subscriber = new AzureSubscriberInterpreter(clientWrapper, queue, messageHandler)

    Resource.pure(subscriber)
  }
  def subscriber[F[_], MessageType: io.circe.Decoder](
    config: AzureServiceBusSubscriberConfig,
    queue: cats.effect.std.Queue[F, AzureEvent[MessageType]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, MessageType]] =
    subscriber(AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper(config), queue)
  def stringSubscriber[F[_]: Async](
    clientWrapper: AzureServiceBusReceiverClientWrapper,
    queue: cats.effect.std.Queue[F, AzureEvent[String]]
  )(implicit logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, String]] = {

    val messageHandler = new StringMessageHandler()

    val subscriber = new AzureSubscriberInterpreter(clientWrapper, queue, messageHandler)

    Resource.pure(subscriber)
  }
  def stringSubscriber[F[_]](
    config: AzureServiceBusSubscriberConfig,
    queue: cats.effect.std.Queue[F, AzureEvent[String]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, String]] =
    stringSubscriber(AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper(config), queue)
}
trait MessageHandler[MessageType] {
  def handleMessage(message: ServiceBusReceivedMessage): Either[Error, AzureEvent[MessageType]]
}

class JsonDecoderMessageHandler[MessageType: Decoder] extends MessageHandler[MessageType] {

  override def handleMessage(message: ServiceBusReceivedMessage): Either[Error, AzureEvent[MessageType]] = {

    val jsonResult = parse(message.getBody.toString)

    jsonResult match {
      case Right(json) =>
        Decoder[MessageType].decodeJson(json) match {
          case Right(decodedData) =>
            val timestamp: Timestamp = ServiceBusMessageUtils.getEnqueuedTimeOrDefault(message)
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

class StringMessageHandler extends MessageHandler[String] {
  override def handleMessage(message: ServiceBusReceivedMessage): Either[Error, AzureEvent[String]] =
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
