package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.std.{Dispatcher, Queue}
import cats.syntax.all._
import com.google.api.core.ApiService
import com.google.api.gax.batching.FlowControlSettings
import com.google.api.gax.core.{FixedCredentialsProvider, FixedExecutorProvider}
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1._
import com.google.common.util.concurrent.{MoreExecutors, ThreadFactoryBuilder}
import com.google.protobuf.Timestamp
import com.google.pubsub.v1.{PubsubMessage, _}
import fs2.Stream
import org.typelevel.log4cats.{Logger, StructuredLogger}
import io.circe.Decoder
import io.circe.parser._
import org.broadinstitute.dsde.workbench.model.TraceId

import java.util.concurrent.ScheduledThreadPoolExecutor
import scala.concurrent.duration.FiniteDuration

private[google2] class GoogleSubscriberInterpreter[F[_], MessageType](
  subscriber: Subscriber,
  queue: cats.effect.std.Queue[F, Event[MessageType]]
)(implicit F: Async[F])
    extends GoogleSubscriber[F, MessageType] {
  val messages: Stream[F, Event[MessageType]] = Stream.fromQueueUnterminated(queue)

  def start: F[Unit] = F.async[Unit] { callback =>
    F.delay {
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
    }.as(None)
  }

  def stop: F[Unit] =
    F.async[Unit] { callback =>
      F.delay {
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
      }.as(None)
    }
}

object GoogleSubscriberInterpreter {
  def apply[F[_], MessageType](
    subscriber: Subscriber,
    queue: cats.effect.std.Queue[F, Event[MessageType]]
  )(implicit F: Async[F]): GoogleSubscriberInterpreter[F, MessageType] =
    new GoogleSubscriberInterpreter[F, MessageType](subscriber, queue)

  private[google2] def receiver[F[_], MessageType: Decoder](
    queue: cats.effect.std.Queue[F, Event[MessageType]],
    dispatcher: Dispatcher[F]
  )(implicit logger: StructuredLogger[F], F: Async[F]): MessageReceiver = new MessageReceiver() {
    override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
      val parseEvent = for {
        isJson <- F.fromEither(parse(message.getData.toStringUtf8)).attempt
        msg <- isJson match {
          case Left(_) =>
            F.raiseError[MessageType](new Exception(s"${message.getData.toStringUtf8} is not a valid Json"))
          case Right(json) =>
            F.fromEither(json.as[MessageType])
        }
        traceId = Option(message.getAttributesMap.get("traceId")).map(s => TraceId(s))
      } yield Event(msg, traceId, message.getPublishTime, consumer)

      val result = for {
        res <- parseEvent.attempt
        _ <- res match {
          case Right(event) =>
            val loggingContext = Map("traceId" -> event.traceId.map(_.asString).getOrElse("None"))

            for {
              r <- queue.offer(event).attempt

              _ <- r match {
                case Left(e) =>
                  logger.info(loggingContext)(s"Subscriber fail to enqueue $message due to $e") >> F.blocking(
                    consumer.nack()
                  ) //pubsub will resend the message up to ackDeadlineSeconds (this is configed during subscription creation)
                case Right(_) =>
                  logger.info(loggingContext)(s"Subscriber Successfully received $message.")
              }
            } yield ()
          case Left(e) =>
            logger
              .info(s"Subscriber fail to decode message ${message} due to ${e}. Going to ack the message") >> F
              .delay(consumer.ack())
        }
      } yield ()

      dispatcher.unsafeRunSync(result)
    }
  }

  private[google2] def stringReceiver[F[_]](queue: cats.effect.std.Queue[F, Event[String]],
                                            dispatcher: Dispatcher[F]
  ): MessageReceiver =
    new MessageReceiver() {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
        val enqueueAction = queue.offer(
          Event(message.getData.toStringUtf8,
                Option(message.getAttributesMap.get("traceId")).map(s => TraceId(s)),
                message.getPublishTime,
                consumer
          )
        )
        dispatcher.unsafeRunSync(enqueueAction)
      }
    }

  def subscriber[F[_]: Async: StructuredLogger, MessageType: Decoder](subscriberConfig: SubscriberConfig,
                                                                      queue: Queue[F, Event[MessageType]],
                                                                      numOfThreads: Int = 20
  ): Resource[F, Subscriber] = {
    val subscription = subscriberConfig.subscriptionName.getOrElse(
      ProjectSubscriptionName.of(subscriberConfig.topicName.getProject, subscriberConfig.topicName.getTopic)
    )

    for {
      credential <- credentialResource(subscriberConfig.pathToCredentialJson)
      subscriptionAdminClient <- subscriptionAdminClientResource(credential)
      _ <- createSubscription(subscriberConfig, subscription, subscriptionAdminClient)
      flowControlSettings = subscriberConfig.flowControlSettingsConfig.map(config =>
        FlowControlSettings.newBuilder
          .setMaxOutstandingElementCount(config.maxOutstandingElementCount)
          .setMaxOutstandingRequestBytes(config.maxOutstandingRequestBytes)
          .build
      )
      sub <- subscriberResource(queue, subscription, credential, flowControlSettings, numOfThreads)
    } yield sub
  }

  def stringSubscriber[F[_]: Async: StructuredLogger](
    subscriberConfig: SubscriberConfig,
    queue: Queue[F, Event[String]]
  ): Resource[F, Subscriber] = {
    val subscription = subscriberConfig.subscriptionName.getOrElse(
      ProjectSubscriptionName.of(subscriberConfig.topicName.getProject, subscriberConfig.topicName.getTopic)
    )

    for {
      credential <- credentialResource(subscriberConfig.pathToCredentialJson)
      subscriptionAdminClient <- subscriptionAdminClientResource(credential)
      _ <- createSubscription(subscriberConfig, subscription, subscriptionAdminClient)
      flowControlSettings = subscriberConfig.flowControlSettingsConfig.map(config =>
        FlowControlSettings.newBuilder
          .setMaxOutstandingElementCount(config.maxOutstandingElementCount)
          .setMaxOutstandingRequestBytes(config.maxOutstandingRequestBytes)
          .build
      )
      dispatcher <- Dispatcher[F]
      sub <- stringSubscriberResource(queue, dispatcher, subscription, credential, flowControlSettings)
    } yield sub
  }

  private def subscriberResource[MessageType: Decoder, F[_]: Async: StructuredLogger](
    queue: Queue[F, Event[MessageType]],
    subscription: ProjectSubscriptionName,
    credential: ServiceAccountCredentials,
    flowControlSettings: Option[FlowControlSettings],
    numOfThreads: Int
  ): Resource[F, Subscriber] = Dispatcher[F].flatMap { d =>
    val threadFactory = new ThreadFactoryBuilder().setNameFormat("goog-subscriber-%d").build()
    val fixedExecutorProvider =
      FixedExecutorProvider.create(new ScheduledThreadPoolExecutor(numOfThreads, threadFactory))

    val subscriber = for {
      builder <- Async[F].blocking(
        Subscriber
          .newBuilder(subscription, receiver(queue, d))
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
          .setExecutorProvider(fixedExecutorProvider)
      )
      builderWithFlowControlSetting <- flowControlSettings.traverse { fcs =>
        Async[F].blocking(builder.setFlowControlSettings(fcs))
      }
    } yield builderWithFlowControlSetting.getOrElse(builder).build()

    Resource.make(subscriber)(s => Async[F].delay(s.stopAsync()))
  }

  private def stringSubscriberResource[F[_]: Sync](
    queue: Queue[F, Event[String]],
    dispatcher: Dispatcher[F],
    subscription: ProjectSubscriptionName,
    credential: ServiceAccountCredentials,
    flowControlSettings: Option[FlowControlSettings]
  ): Resource[F, Subscriber] = {
    val subscriber = for {
      builder <- Sync[F].delay(
        Subscriber
          .newBuilder(subscription, stringReceiver(queue, dispatcher))
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
      )
      builderWithFlowControlSetting <- flowControlSettings.traverse { fcs =>
        Sync[F].delay(builder.setFlowControlSettings(fcs))
      }
    } yield builderWithFlowControlSetting.getOrElse(builder).build()

    Resource.make(subscriber)(s => Sync[F].delay(s.stopAsync()))
  }

  private def createSubscription[F[_]: Logger](
    subscriberConfig: SubscriberConfig,
    subscription: ProjectSubscriptionName,
    subscriptionAdminClient: SubscriptionAdminClient
  )(implicit F: Async[F]): Resource[F, Unit] = {
    val initialSub = Subscription
      .newBuilder()
      .setName(subscription.toString)
      .setTopic(subscriberConfig.topicName.toString)
      .setPushConfig(PushConfig.getDefaultInstance)
      .setAckDeadlineSeconds(subscriberConfig.ackDeadLine.toSeconds.toInt)

    val subWithDeadLetterPolicy = subscriberConfig.deadLetterPolicy.fold(initialSub) { deadLetterPolicy =>
      initialSub
        .setDeadLetterPolicy(
          DeadLetterPolicy
            .newBuilder()
            .setDeadLetterTopic(deadLetterPolicy.topicName.toString)
            .setMaxDeliveryAttempts(deadLetterPolicy.maxRetries.value)
            .build()
        )
    }

    val sub = subscriberConfig.filter.fold(subWithDeadLetterPolicy.build()) { ft =>
      subWithDeadLetterPolicy.setFilter(ft).build()
    }

    Resource.eval(
      F.blocking(
        subscriptionAdminClient.createSubscription(sub)
      ).void
        .recover { case _: AlreadyExistsException =>
          Logger[F].info(s"subscription ${subscription} already exists")
        }
    )
  }

  private def subscriptionAdminClientResource[F[_]: Async](credential: ServiceAccountCredentials) =
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
final case class SubscriberConfig(
  pathToCredentialJson: String,
  topicName: TopicName,
  subscriptionName: Option[ProjectSubscriptionName], //it'll have the same name as topic if this is None
  ackDeadLine: FiniteDuration,
  deadLetterPolicy: Option[SubscriberDeadLetterPolicy],
  flowControlSettingsConfig: Option[FlowControlSettingsConfig],
  filter: Option[String]
)
final case class MaxRetries(value: Int) extends AnyVal
final case class SubscriberDeadLetterPolicy(topicName: TopicName, maxRetries: MaxRetries)

final case class Event[A](msg: A, traceId: Option[TraceId] = None, publishedTime: Timestamp, consumer: AckReplyConsumer)
