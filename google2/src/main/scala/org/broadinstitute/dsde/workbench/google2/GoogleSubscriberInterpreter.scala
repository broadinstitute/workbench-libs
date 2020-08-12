package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import com.google.api.core.ApiService
import com.google.api.gax.batching.FlowControlSettings
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1._
import com.google.common.util.concurrent.MoreExecutors
import com.google.pubsub.v1.{PubsubMessage, _}
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.{Logger, StructuredLogger}
import io.circe.Decoder
import io.circe.parser._
import org.broadinstitute.dsde.workbench.model.TraceId
import com.google.protobuf.Timestamp

import scala.concurrent.duration.FiniteDuration

private[google2] class GoogleSubscriberInterpreter[F[_]: Async: Timer: ContextShift, MessageType](
  subscriber: Subscriber,
  queue: fs2.concurrent.Queue[F, Event[MessageType]]
) extends GoogleSubscriber[F, MessageType] {
  val messages: Stream[F, Event[MessageType]] = queue.dequeue

  def start: F[Unit] = Async[F].async[Unit] { callback =>
    subscriber.addListener(
      new ApiService.Listener() {
        override def failed(from: ApiService.State, failure: Throwable): Unit =
          callback(Left(failure))
        override def terminated(from: ApiService.State): Unit =
          callback(Right(()))
      },
      MoreExecutors.directExecutor()
    )
    subscriber.startAsync()
  }

  def stop: F[Unit] =
    Async[F].async[Unit] { callback =>
      subscriber.addListener(
        new ApiService.Listener() {
          override def failed(from: ApiService.State, failure: Throwable): Unit =
            callback(Left(failure))
          override def terminated(from: ApiService.State): Unit =
            callback(Right(()))
        },
        MoreExecutors.directExecutor()
      )
      subscriber.stopAsync()
    }
}

object GoogleSubscriberInterpreter {
  def apply[F[_]: Async: Timer: ContextShift, MessageType](
    subscriber: Subscriber,
    queue: fs2.concurrent.Queue[F, Event[MessageType]]
  ): GoogleSubscriberInterpreter[F, MessageType] = new GoogleSubscriberInterpreter[F, MessageType](subscriber, queue)

  private[google2] def receiver[F[_]: Effect, MessageType: Decoder](
    queue: fs2.concurrent.Queue[F, Event[MessageType]]
  )(implicit logger: StructuredLogger[F]): MessageReceiver = new MessageReceiver() {
    override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
      val parseEvent = for {
        isJson <- Effect[F].fromEither(parse(message.getData.toStringUtf8)).attempt
        msg <- isJson match {
          case Left(_) =>
            Effect[F].raiseError[MessageType](new Exception(s"${message.getData.toStringUtf8} is not a valid Json"))
          case Right(json) =>
            Effect[F].fromEither(json.as[MessageType])
        }
        traceId = Option(message.getAttributesMap.get("traceId")).map(s => TraceId(s))
      } yield Event(msg, traceId, message.getPublishTime, consumer)

      val loggingCtx = Map(
        "traceId" -> Option(message.getAttributesMap.get("traceId")).getOrElse(""),
        "publishTime" -> message.getPublishTime.toString
      )

      val result = for {
        res <- parseEvent.attempt
        _ <- res match {
          case Right(event) =>
            for {
              r <- queue.enqueue1(event).attempt

              _ <- r match {
                case Left(e) =>
                  logger.info(loggingCtx)(s"Subscriber fail to enqueue $message due to $e") >> Effect[F].delay(
                    consumer.nack()
                  ) //pubsub will resend the message up to ackDeadlineSeconds (this is configed during subscription creation)
                case Right(_) =>
                  logger.info(loggingCtx)(s"Subscriber Successfully received $message.")
              }
            } yield ()
          case Left(e) =>
            logger
              .info(s"Subscriber fail to decode message ${message} due to ${e}. Going to ack the message") >> Effect[F]
              .delay(consumer.ack())
        }
      } yield ()

      result.toIO.unsafeRunSync
    }
  }

  private[google2] def stringReceiver[F[_]: Effect](queue: fs2.concurrent.Queue[F, Event[String]]): MessageReceiver =
    new MessageReceiver() {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
        val enqueueAction = queue.enqueue1(
          Event(message.getData.toStringUtf8,
                Option(message.getAttributesMap.get("traceId")).map(s => TraceId(s)),
                message.getPublishTime,
                consumer)
        )
        enqueueAction.toIO.unsafeRunSync
      }
    }

  def subscriber[F[_]: Effect: StructuredLogger, MessageType: Decoder](
    subscriberConfig: SubscriberConfig,
    queue: fs2.concurrent.Queue[F, Event[MessageType]]
  ): Resource[F, Subscriber] = {
    val subscription =
      ProjectSubscriptionName.of(subscriberConfig.topicName.getProject, subscriberConfig.topicName.getTopic)

    for {
      credential <- credentialResource(subscriberConfig.pathToCredentialJson)
      subscriptionAdminClient <- subscriptionAdminClientResource(credential)
      _ <- createSubscription(subscriberConfig, subscription, subscriptionAdminClient)
      flowControlSettings = subscriberConfig.flowControlSettingsConfig.map(
        config =>
          FlowControlSettings.newBuilder
            .setMaxOutstandingElementCount(config.maxOutstandingElementCount)
            .setMaxOutstandingRequestBytes(config.maxOutstandingRequestBytes)
            .build
      )
      sub <- subscriberResource(queue, subscription, credential, flowControlSettings)
    } yield sub
  }

  def stringSubscriber[F[_]: Effect: StructuredLogger](
    subscriberConfig: SubscriberConfig,
    queue: fs2.concurrent.Queue[F, Event[String]]
  ): Resource[F, Subscriber] = {
    val subscription =
      ProjectSubscriptionName.of(subscriberConfig.topicName.getProject, subscriberConfig.topicName.getTopic)

    for {
      credential <- credentialResource(subscriberConfig.pathToCredentialJson)
      subscriptionAdminClient <- subscriptionAdminClientResource(credential)
      _ <- createSubscription(subscriberConfig, subscription, subscriptionAdminClient)
      flowControlSettings = subscriberConfig.flowControlSettingsConfig.map(
        config =>
          FlowControlSettings.newBuilder
            .setMaxOutstandingElementCount(config.maxOutstandingElementCount)
            .setMaxOutstandingRequestBytes(config.maxOutstandingRequestBytes)
            .build
      )
      sub <- stringSubscriberResource(queue, subscription, credential, flowControlSettings)
    } yield sub
  }

  private def subscriberResource[MessageType: Decoder, F[_]: Effect: StructuredLogger](
    queue: Queue[F, Event[MessageType]],
    subscription: ProjectSubscriptionName,
    credential: ServiceAccountCredentials,
    flowControlSettings: Option[FlowControlSettings]
  ): Resource[F, Subscriber] = {
    val subscriber = for {
      builder <- Sync[F].delay(
        Subscriber
          .newBuilder(subscription, receiver(queue))
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
      )
      builderWithFlowControlSetting <- flowControlSettings.traverse { fcs =>
        Sync[F].delay(builder.setFlowControlSettings(fcs))
      }
    } yield builderWithFlowControlSetting.getOrElse(builder).build()

    Resource.make(subscriber)(s => Sync[F].delay(s.stopAsync()))
  }

  private def stringSubscriberResource[F[_]: Effect](
    queue: Queue[F, Event[String]],
    subscription: ProjectSubscriptionName,
    credential: ServiceAccountCredentials,
    flowControlSettings: Option[FlowControlSettings]
  ): Resource[F, Subscriber] = {
    val subscriber = for {
      builder <- Sync[F].delay(
        Subscriber
          .newBuilder(subscription, stringReceiver(queue))
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
      )
      builderWithFlowControlSetting <- flowControlSettings.traverse { fcs =>
        Sync[F].delay(builder.setFlowControlSettings(fcs))
      }
    } yield builderWithFlowControlSetting.getOrElse(builder).build()

    Resource.make(subscriber)(s => Sync[F].delay(s.stopAsync()))
  }

  private def createSubscription[F[_]: Effect: Logger](
    subsriberConfig: SubscriberConfig,
    subscription: ProjectSubscriptionName,
    subscriptionAdminClient: SubscriptionAdminClient
  ): Resource[F, Unit] = {
    val sub = Subscription
      .newBuilder()
      .setName(subscription.toString)
      .setTopic(subsriberConfig.topicName.toString)
      .setPushConfig(PushConfig.getDefaultInstance)
      .setAckDeadlineSeconds(subsriberConfig.ackDeadLine.toSeconds.toInt)
//   Comment this out since this causes this error: INVALID_ARGUMENT: Invalid resource name given (name=). Refer to https://cloud.google.com/pubsub/docs/admin#resource_names for more information
//      .setDeadLetterPolicy(
//        DeadLetterPolicy.newBuilder().setMaxDeliveryAttempts(subsriberConfig.maxRetries.value).build()
//      )
      .build()
    Resource.liftF(
      Async[F]
        .delay(
          subscriptionAdminClient.createSubscription(sub)
        )
        .void
        .recover {
          case _: AlreadyExistsException => Logger[F].info(s"subscription ${subscription} already exists")
        }
    )
  }

  private def subscriptionAdminClientResource[F[_]: Effect: Logger](credential: ServiceAccountCredentials) =
    Resource.make[F, SubscriptionAdminClient](
      Async[F].delay(
        SubscriptionAdminClient.create(
          SubscriptionAdminSettings
            .newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credential))
            .build()
        )
      )
    )(client => Async[F].delay(client.shutdown()))
}

final case class FlowControlSettingsConfig(maxOutstandingElementCount: Long, maxOutstandingRequestBytes: Long)
final case class SubscriberConfig(pathToCredentialJson: String,
                                  topicName: TopicName,
                                  ackDeadLine: FiniteDuration,
                                  maxRetries: MaxRetries,
                                  flowControlSettingsConfig: Option[FlowControlSettingsConfig])
final case class MaxRetries(value: Int) extends AnyVal
final case class Event[A](msg: A, traceId: Option[TraceId] = None, publishedTime: Timestamp, consumer: AckReplyConsumer)
