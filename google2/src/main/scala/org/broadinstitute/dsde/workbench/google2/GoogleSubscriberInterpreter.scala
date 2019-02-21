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
import io.chrisdavenport.log4cats.Logger
import io.circe.Decoder
import io.circe.parser._

import scala.concurrent.duration.FiniteDuration

private[google2] class GoogleSubscriberInterpreter[F[_]: Async: Timer: ContextShift, MessageType](
                                                                                         subscriber: Subscriber,
                                                                                         queue: fs2.concurrent.Queue[F, Event[MessageType]]
                                                                                       ) extends GoogleSubscriber[F, MessageType] {
  val messages: Stream[F, Event[MessageType]] = queue.dequeue

  def start: F[Unit] = Async[F].async[Unit]{
    callback =>
      subscriber.addListener(
        new ApiService.Listener() {
          override def failed(from: ApiService.State, failure: Throwable): Unit = {
            callback(Left(failure))
          }
          override def terminated(from: ApiService.State): Unit = {
            callback(Right(()))
          }
        },
        MoreExecutors.directExecutor()
      )
      subscriber.startAsync()
  }

  def stop: F[Unit] =
    Async[F].async[Unit]{
      callback =>
        subscriber.addListener(
          new ApiService.Listener() {
            override def failed(from: ApiService.State, failure: Throwable): Unit = {
              callback(Left(failure))
            }
            override def terminated(from: ApiService.State): Unit = {
              callback(Right(()))
            }
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

  def receiver[F[_]: Effect: Logger, MessageType: Decoder](queue: fs2.concurrent.Queue[F, Event[MessageType]]): MessageReceiver = new MessageReceiver() {
    override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
      val result = for {
        json <- parse(message.getData.toStringUtf8)
        message <- json.as[MessageType]
        _ <- queue.enqueue1(Event(message, consumer)).attempt.toIO.unsafeRunSync()
      } yield ()

      result match {
        case Left(e) =>
          Logger[F].info(s"failed to publish $message to internal Queue due to $e")
          consumer.nack() //pubsub will resend the message up to ackDeadlineSeconds (this is configed during subscription creation
        case Right(_) =>
          ()
      }
    }
  }

  def subscriber[F[_]: Effect: Logger, MessageType: Decoder](subscriberConfig: SubscriberConfig, queue: fs2.concurrent.Queue[F, Event[MessageType]]): Resource[F, Subscriber] = {
    val subscription = ProjectSubscriptionName.of(subscriberConfig.projectTopicName.getProject, subscriberConfig.projectTopicName.getTopic)

    for {
      credential <- credentialResource(subscriberConfig.pathToCredentialJson)
      subscriptionAdminClient <- subscriptionAdminClientResource(credential)
      _ <- createSubscription(subscriberConfig, subscription, subscriptionAdminClient)
      flowControlSettings = FlowControlSettings
        .newBuilder
        .setMaxOutstandingElementCount(subscriberConfig.flowControlSettingsConfig.maxOutstandingElementCount)
        .setMaxOutstandingRequestBytes(subscriberConfig.flowControlSettingsConfig.maxOutstandingRequestBytes)
        .build
      sub <- subscriberResource(queue, subscription, credential, flowControlSettings)
    } yield sub
  }

  private def subscriberResource[MessageType: Decoder, F[_] : Effect : Logger](queue: Queue[F, Event[MessageType]], subscription: ProjectSubscriptionName, credential: ServiceAccountCredentials, flowControlSettings: FlowControlSettings) = {
    Resource.make(
      Sync[F].delay(
        Subscriber
          .newBuilder(subscription, receiver(queue))
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
          .setFlowControlSettings(flowControlSettings)
          .build())
    )(s => Sync[F].delay(s.stopAsync()))
  }

  private def createSubscription[MessageType: Decoder, F[_] : Effect : Logger](subsriberConfig: SubscriberConfig, subscription: ProjectSubscriptionName, subscriptionAdminClient: SubscriptionAdminClient) = {
    Resource.liftF(Async[F].delay(
      subscriptionAdminClient.createSubscription(subscription, subsriberConfig.projectTopicName, PushConfig.getDefaultInstance, subsriberConfig.ackDeadLine.toSeconds.toInt)
    ).void.recover {
      case _: AlreadyExistsException => ()
    })
  }

  private def subscriptionAdminClientResource[MessageType: Decoder, F[_] : Effect : Logger](credential: ServiceAccountCredentials) = {
    Resource.make[F, SubscriptionAdminClient](Async[F].delay(
      SubscriptionAdminClient.create(
        SubscriptionAdminSettings.newBuilder()
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
          .build()))
    )(client => Async[F].delay(client.shutdown()))
  }
}

final case class FlowControlSettingsConfig(maxOutstandingElementCount: Long, maxOutstandingRequestBytes: Long)
final case class SubscriberConfig(pathToCredentialJson: String, projectTopicName: ProjectTopicName, ackDeadLine: FiniteDuration, flowControlSettingsConfig: FlowControlSettingsConfig)
final case class Event[A](msg: A, consumer: AckReplyConsumer)