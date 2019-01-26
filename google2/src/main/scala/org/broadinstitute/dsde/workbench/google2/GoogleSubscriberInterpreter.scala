package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import com.google.api.core.ApiService
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1._
import com.google.common.util.concurrent.MoreExecutors
import com.google.pubsub.v1.{PubsubMessage, _}
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.circe.Decoder
import io.circe.parser._
import scala.concurrent.duration.FiniteDuration

private[google2] class GoogleSubscriberInterpreter[F[_]: Async: Timer: ContextShift, A](
                                                         subscriber: Subscriber,
                                                         queue: fs2.concurrent.Queue[F, Event[A]]
                                     ) extends GoogleSubscriber[F, A] {
  val messages: Stream[F, Event[A]] = queue.dequeue

  def start: F[Unit] = Async[F].async[Unit]{
        cb =>
          subscriber.addListener(
            new ApiService.Listener() {
              override def failed(from: ApiService.State, failure: Throwable): Unit = {
                cb(Left(failure))
              }
              override def terminated(from: ApiService.State): Unit = {
                cb(Right(()))
              }
            },
            MoreExecutors.directExecutor()
          )
          subscriber.startAsync()
      }

  def stop: F[Unit] =
    Async[F].async[Unit]{
      cb =>
        subscriber.addListener(
          new ApiService.Listener() {
            override def failed(from: ApiService.State, failure: Throwable): Unit = {
              cb(Left(failure))
            }
            override def terminated(from: ApiService.State): Unit = {
              cb(Right(()))
            }
          },
          MoreExecutors.directExecutor()
        )
        subscriber.stopAsync()
    }
}

object GoogleSubscriberInterpreter {
  def apply[F[_]: Async: Timer: ContextShift, A](
                                               subscriber: Subscriber,
                                               queue: fs2.concurrent.Queue[F, Event[A]]
                                             ): GoogleSubscriberInterpreter[F, A] = new GoogleSubscriberInterpreter[F, A](subscriber, queue)

  def receiver[F[_]: Effect: Logger, A:Decoder](queue: fs2.concurrent.Queue[F, Event[A]]): MessageReceiver = new MessageReceiver() {
    override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
      val result = for {
        json <- parse(message.getData.toStringUtf8)
        a <- json.as[A]
        _ <- queue.enqueue1(Event(a, consumer)).attempt.toIO.unsafeRunSync()
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

  def subscriber[F[_]: Effect: Logger, A: Decoder](subsriberConfig: SubscriberConfig, queue: fs2.concurrent.Queue[F, Event[A]]): Resource[F, Subscriber] = {
    val subscription = ProjectSubscriptionName.of(subsriberConfig.projectTopicName.getProject, subsriberConfig.projectTopicName.getTopic)

    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util.readFile(subsriberConfig.pathToCredentialJson)
      credential = ServiceAccountCredentials.fromStream(credentialFile)
      subscriptionAdminClient <- Resource.make[F, SubscriptionAdminClient](Async[F].delay(
        SubscriptionAdminClient.create(
          SubscriptionAdminSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credential))
            .build()))
      )(client => Async[F].delay(client.shutdown()))
      _ <- Resource.liftF(Async[F].delay(
        subscriptionAdminClient.createSubscription(subscription, subsriberConfig.projectTopicName, PushConfig.getDefaultInstance, subsriberConfig.achDeadLine.toSeconds.toInt)
      ).void.recover{
        case _: com.google.api.gax.rpc.AlreadyExistsException => ()
      })
      sub <- Resource.make(
        Sync[F].delay(
          Subscriber
          .newBuilder(subscription, receiver(queue)) //TODO: set credentials correctly
            .setCredentialsProvider(FixedCredentialsProvider.create(credential))
            .build())
      )(s => Sync[F].delay(s.stopAsync()))
    } yield sub
  }
}

final case class SubscriberConfig(pathToCredentialJson: String, projectTopicName: ProjectTopicName, achDeadLine: FiniteDuration)
final case class Event[A](msg: A, consumer: AckReplyConsumer)