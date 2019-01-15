package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.core.ApiService
import com.google.cloud.pubsub.v1._
import com.google.common.util.concurrent.MoreExecutors
import com.google.pubsub.v1.{PubsubMessage, _}
import io.chrisdavenport.log4cats.Logger
import io.circe.Decoder
import io.circe.parser._
import io.grpc.Status.Code

import scala.concurrent.duration.FiniteDuration

private[google2] class GoogleSubscriberInterpreter[F[_]: Async: Timer: ContextShift](
                                                         subscriber: Subscriber
                                     ) extends GoogleSubscriber[F] {
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
  def apply[F[_]: Async: Timer: ContextShift](
                                               subscriber: Subscriber
                                             ): GoogleSubscriberInterpreter[F] = new GoogleSubscriberInterpreter[F](subscriber)

  def subscriptionAdminClient[F[_]: Async](pathToJson: String): Resource[F, SubscriptionAdminClient] = {
    Resource.make[F, SubscriptionAdminClient](Async[F].delay(
      SubscriptionAdminClient.create(
        SubscriptionAdminSettings.newBuilder()
//          .setCredentialsProvider(credentialsProvider) TODO: use correct credential
          .build()))
    )(client => Async[F].delay(client.shutdown()))
  }

  def subscriber[F[_]: Effect: Logger, A: Decoder](pathToJson: String, subsriberConfig: SubsriberConfig, handler: A => F[Unit]): Resource[F, Subscriber] = {
    val receiver = new MessageReceiver() {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
        val result = for {
          json <- parse(message.getData.toStringUtf8)
          a <- json.as[A]
          _ <- handler(a).attempt.toIO.unsafeRunSync()
        } yield ()

        result match {
          case Left(e) =>
            Logger[F].info(s"failed to process $message due to $e")
            consumer.nack() //pubsub will resend the message up to ackDeadlineSeconds (this is configed during subscription creation
          case Right(_) =>
            consumer.ack()
        }
      }
    }

    val subscription = ProjectSubscriptionName.of(subsriberConfig.projectTopicName.getProject, subsriberConfig.projectTopicName.getTopic)

    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util.readFile(pathToJson)
      //      credentialBuilder = new GoogleCredential.Builder()
      //        .setServiceAccountPrivateKeyFromPemFile(pem)
      //        .build()
      credential = GoogleCredential.fromStream(credentialFile)
      subscriptionAdminClient <- subscriptionAdminClient(pathToJson)
      _ <- Resource.liftF(Async[F].delay(
        subscriptionAdminClient.createSubscription(subscription, subsriberConfig.projectTopicName, PushConfig.getDefaultInstance, subsriberConfig.achDeadLine.toSeconds.toInt)
      ).void.recover{
        case e: io.grpc.StatusRuntimeException if(e.getStatus.getCode == Code.ALREADY_EXISTS) => ()
      })
      sub <- Resource.make(
        Sync[F].delay(
          Subscriber
          .newBuilder(subscription, receiver) //TODO: set credentials correctly
          .build())
      )(s => Sync[F].delay(s.stopAsync()))
    } yield sub
  }
}

final case class SubsriberConfig(projectTopicName: ProjectTopicName, achDeadLine: FiniteDuration)