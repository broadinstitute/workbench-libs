package org.broadinstitute.dsde.workbench.azure

import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits._
import com.azure.messaging.servicebus.ServiceBusReceivedMessage
import io.circe.Decoder
import io.circe.parser.parse
import org.typelevel.log4cats.StructuredLogger
import reactor.core.Disposable

private[azure] class AzureSubscriberInterpreter[F[_], MessageType](
  clientWrapper: AzureServiceBusReceiverClientWrapper,
  queue: cats.effect.std.Queue[F, AzureEvent[MessageType]],
  messageHandler: MessageHandler[MessageType],
  dispatcher: Dispatcher[F]
)(implicit F: Async[F], logger: StructuredLogger[F])
    extends AzureSubscriber[F, MessageType] {
//  private var subscription: Option[Disposable] = ???

  override def messages: fs2.Stream[F, AzureEvent[MessageType]] = fs2.Stream.fromQueueUnterminated(queue)

//  private def setSubscription(disposable: Disposable): Unit =
//    subscription = Option(disposable)

//  override def initSubscriber: Resource[F, Disposable] = Resource.make(F.async[Disposable] { callback =>
//    F.delay(
//        // The client returns a Flux<ServiceBusReceivedMessage>, a subscription
//        // to the flux is functionally equivalent to starting a background message pump.
//        // The subscription returns a Disposable, which can be used to stop the processing.
//        clientWrapper
//          .receiveMessagesAsync()
//          .subscribe(
//            (message: ServiceBusReceivedMessage) => handleMessage(callback, message),
//            (failure: Throwable) => handleReceiveError(callback, failure)
//          )
//    ).as(None)
//  })(_ => F.unit)

  override def start: F[Unit] = F.unit // TODO: remove

  def initSubscription: Resource[F, Disposable] = Resource.make(
    F.delay(
      clientWrapper
        .receiveMessagesAsync()
        .subscribe(
          (message: ServiceBusReceivedMessage) => handleMessage(message),
          (failure: Throwable) => handleReceiveError(failure)
        )
    )
  )(d => F.delay(d.dispose()))

//    F.async[Unit] { callback =>
//    F.delay(
//      setSubscription(
//        // The client returns a Flux<ServiceBusReceivedMessage>, a subscription
//        // to the flux is functionally equivalent to starting a background message pump.
//        // The subscription returns a Disposable, which can be used to stop the processing.
//        clientWrapper
//          .receiveMessagesAsync()
//          .subscribe(
//            (message: ServiceBusReceivedMessage) => handleMessage(callback, message),
//            (failure: Throwable) => handleReceiveError(callback, failure)
//          )
//      )
//    ).as(None)
//  }

  private def handleReceiveError(failure: Throwable): Unit = {
    val handleError = for {
      _ <- logger.error(s"Error in message subscription: ${failure.getMessage}")
//      _ <- F.delay(callback(Left(failure)))
    } yield ()
    dispatcher.unsafeRunSync(handleError)
  }

  private def handleMessage(message: ServiceBusReceivedMessage): Unit = {
    val loggingContext = Map("traceId" -> Option(message.getCorrelationId).getOrElse("None"))

    messageHandler.handleMessage(message) match {
      case Right(value) =>
        val enqueue = for {
          _ <- logger.info(loggingContext)(s"Subscriber Successfully received $message.")
          _ <- queue.offer(value)
          _ <- F.delay(clientWrapper.complete(message))
//          _ <- F.delay(callback(Right(())))
        } yield ()
        dispatcher.unsafeRunSync(enqueue)
      case Left(exception) =>
        val handleError = for {
          _ <- logger.error(loggingContext)(s"Subscriber failed to process $message due to ${exception.getMessage}")
          _ <- F.delay(clientWrapper.abandon(message))
//          _ <- F.delay(callback(Left(exception)))
        } yield ()
        dispatcher.unsafeRunSync(handleError)
    }
  }

  override def stop: F[Unit] = F.delay {
    // Stop processor and dispose when done processing messages.
    clientWrapper.close()
  }
}

object AzureSubscriberInterpreter {

  def subscriber[F[_], MessageType: io.circe.Decoder](
    clientWrapper: AzureServiceBusReceiverClientWrapper,
    queue: cats.effect.std.Queue[F, AzureEvent[MessageType]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, MessageType]] =
    for {
      dispatcher <- Dispatcher.sequential[F]
      messageHandler = new JsonDecoderMessageHandler[MessageType]

      subscriberInterp = new AzureSubscriberInterpreter(clientWrapper, queue, messageHandler, dispatcher)

      sub <- Resource.make(F.pure(subscriberInterp))(_ => subscriberInterp.stop)
      _ <- subscriberInterp.initSubscription
    } yield sub

  def subscriber[F[_], MessageType: io.circe.Decoder](
    config: AzureServiceBusSubscriberConfig,
    queue: cats.effect.std.Queue[F, AzureEvent[MessageType]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, MessageType]] =
    subscriber(AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper(config), queue)

  def stringSubscriber[F[_]](
    clientWrapper: AzureServiceBusReceiverClientWrapper,
    queue: cats.effect.std.Queue[F, AzureEvent[String]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, String]] =
    for {
      dispatcher <- Dispatcher.sequential[F]
      messageHandler = new StringMessageHandler()

      subscriber = new AzureSubscriberInterpreter[F, String](clientWrapper, queue, messageHandler, dispatcher)

      _ <- subscriber.initSubscription
      sub <- Resource.make(F.pure(subscriber))(_ => subscriber.stop)
    } yield sub

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
