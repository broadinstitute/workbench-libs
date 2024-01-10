package org.broadinstitute.dsde.workbench.azure

import cats.effect._
import cats.syntax.all._
import cats.implicits.catsSyntaxApplicativeError
import com.azure.messaging.servicebus.ServiceBusReceivedMessage
import com.google.protobuf.Timestamp
import io.circe.Decoder
import io.circe.parser._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.log4cats.StructuredLogger
import reactor.core.Disposable

import scala.util.{Failure, Success, Try}

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
      print("Starting subscriber")
      val disposable: Disposable = clientWrapper
        .receiveMessagesAsync()
        .subscribe(
          (message: ServiceBusReceivedMessage) => {

            val loggingContext = Map("traceId" -> Option(message.getCorrelationId).getOrElse("None"))

            messageHandler.handleMessage(message) match {
              case Success(value) =>
                logger.info(loggingContext)(s"Subscriber Successfully received $message.")

                queue.offer(value).attempt

                clientWrapper.complete(message)

                callback(Right())
              case Failure(exception) =>
                logger.info(loggingContext)(s"Subscriber fail to enqueue $message due to $exception")

                clientWrapper.abandon(message)

                callback(Left(exception))
            }
          },
          (failure: Throwable) => callback(Left(failure))
        )
      subscription = Some(disposable)
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
  def stringSubscriber[F[_]](
    clientWrapper: AzureServiceBusReceiverClientWrapper,
    queue: cats.effect.std.Queue[F, AzureEvent[String]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, String]] = {

    val messageHandler = new StringMessageHandler()

    val subscriber = new AzureSubscriberInterpreter(clientWrapper, queue, messageHandler)

    Resource.pure(subscriber)
  }
  def stringSubscriber[F[_]](
    config: AzureServiceBusSubscriberConfig,
    queue: cats.effect.std.Queue[F, AzureEvent[String]]
  )(implicit F: Async[F], logger: StructuredLogger[F]): cats.effect.Resource[F, AzureSubscriber[F, String]] =
    stringSubscriber(AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper(config), queue)

//  private def createReceiverClient(
//    subscriberConfig: AzureServiceBusSubscriberConfig
//  ): ServiceBusReceiverAsyncClient =
//    new ServiceBusClientBuilder()
//      .connectionString(subscriberConfig.connectionString.get)
//      .receiver()
//      .topicName(subscriberConfig.topicName)
//      .subscriptionName(subscriberConfig.subscriptionName)
//      .buildAsyncClient()
}
trait MessageHandler[MessageType] {
  def handleMessage(message: ServiceBusReceivedMessage): Try[AzureEvent[MessageType]]
}

class JsonDecoderMessageHandler[MessageType: Decoder] extends MessageHandler[MessageType] {

  override def handleMessage(message: ServiceBusReceivedMessage): Try[AzureEvent[MessageType]] = {

    val json = parse(message.getBody.toString)
    json match {
      case Left(ex) =>
        Failure(ex)
      case Right(json) =>

        val timestamp: Timestamp = ServiceBusMessageUtils.getEnqueuedTimeOrDefault(message)

        val traceId = Option(message.getCorrelationId).map(s => TraceId(s))

        val result = Decoder[MessageType].decodeJson(json)

        Try(AzureEvent(result.getOrElse(throw new RuntimeException("Failed to decode message")), traceId, timestamp))
    }
  }

}

class StringMessageHandler extends MessageHandler[String] {
  override def handleMessage(message: ServiceBusReceivedMessage): Try[AzureEvent[String]] =
    try {

      val timestamp = ServiceBusMessageUtils.getEnqueuedTimeOrDefault(message)

      val traceId = Option(message.getCorrelationId).map(c => TraceId(c))

      Try(AzureEvent(message.getBody.toString, traceId, timestamp))
    } catch {
      case e: Exception =>
        Failure(e)
    }
}

