package org.broadinstitute.dsde.workbench.google2

import cats.effect.std.{Dispatcher, Queue}
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1._
import com.google.pubsub.v1.{ProjectSubscriptionName, PushConfig, TopicName}
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.circe.Decoder
import io.circe.generic.auto._
import io.grpc.ManagedChannelBuilder
import io.grpc.Status.Code
import org.broadinstitute.dsde.workbench.google2.GooglePubSubSpec._
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.broadinstitute.dsde.workbench.util2.messaging.ReceivedMessage
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.StructuredLogger

import scala.concurrent.duration._
import scala.util.Try

class GooglePubSubSpec extends AnyFlatSpecLike with Matchers with WorkbenchTestSuite {
  "GooglePublisherInterpreter" should "be able to publish message successfully" in {
    val people = Generators.genListPerson.sample.get
    val topicName = Generators.genTopicName.sample.get

    val res = for {
      queue <- cats.effect.std.Queue.bounded[IO, ReceivedMessage[Person]](10000)
      _ <- localPubsub[Person](topicName, queue).use { case (pub, _) =>
        val res = Stream.emits(people) through pub.publish

        res.compile.drain
      }
    } yield succeed

    res.unsafeRunSync()
  }

  "GoogleSubscriberInterpreter" should "be able to subscribe messages successfully" in {
    val people = Generators.genListPerson.sample.get
    val projectTopicName = Generators.genTopicName.sample.get

    var expectedPeople = people
    val res = for {
      queue <- Queue.bounded[IO, ReceivedMessage[Person]](10000)
      terminateSubscriber <- SignallingRef[IO, Boolean](false) // signal for terminating subscriber
      terminateStopStream <- SignallingRef[IO, Boolean](false) // signal for terminating stopStream
      _ <- localPubsub(projectTopicName, queue).use { case (pub, sub) =>
        val subScribeStream = (Stream.emits(people) through pub.publish[Person]) ++ Stream.eval(sub.start)

        val processEvents: Stream[IO, Unit] = sub.messages.zipWithIndex
          .evalMap[IO, Unit] { case (event, index) =>
            if (expectedPeople.contains(event.msg)) {
              expectedPeople = expectedPeople.filterNot(_ == event.msg)
              if (index.toInt == people.length - 1)
                IO(event.ackHandler.ack()).void >> terminateSubscriber.set(true)
              else
                IO(event.ackHandler.ack()).void
            } else
              IO.raiseError(new Exception(s"$event doesn't equal ${people(index.toInt)}")) >> terminateSubscriber
                .set(true)
          }
          .interruptWhen(terminateStopStream)

        // stopStream will check every 1 seconds to see if SignallingRef is set to false, if so terminate subscriber
        val stopStream: Stream[IO, Unit] = (Stream.sleep[IO](1 seconds) ++ Stream.eval_ {
          for {
            r <- terminateSubscriber.get
            _ <-
              if (r) {
                sub.stop >> terminateStopStream.set(true)
              } else IO.unit
          } yield ()
        }).repeat.interruptWhen(terminateStopStream)

        val finalStream = Stream(subScribeStream, stopStream, processEvents).parJoinUnbounded
        finalStream.compile.drain
      }
    } yield succeed

    res.unsafeRunSync()
  }
}

object GooglePubSubSpec {
  def localPubsub[A: Decoder](projectTopicName: TopicName, queue: Queue[IO, ReceivedMessage[A]])(implicit
    logger: StructuredLogger[IO]
  ): Resource[IO, (GooglePublisherInterpreter[IO], GoogleSubscriberInterpreter[IO, A])] =
    for {
      channel <- Resource.make(IO(ManagedChannelBuilder.forTarget("localhost:8085").usePlaintext().build()))(c =>
        IO(c.shutdown())
      )
      channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel))
      credentialsProvider = NoCredentialsProvider.create()
      topicClient <- Resource.make(
        IO(
          TopicAdminClient.create(
            TopicAdminSettings
              .newBuilder()
              .setTransportChannelProvider(channelProvider)
              .setCredentialsProvider(credentialsProvider)
              .build()
          )
        )
      )(client => IO(client.shutdown()))
      subscriptionAdminClient <- Resource.make(
        IO(
          SubscriptionAdminClient.create(
            SubscriptionAdminSettings
              .newBuilder()
              .setTransportChannelProvider(channelProvider)
              .setCredentialsProvider(credentialsProvider)
              .build()
          )
        )
      )(client => IO(client.shutdown()))
      pub <- Resource.make(
        IO(
          Publisher
            .newBuilder(projectTopicName)
            .setChannelProvider(channelProvider)
            .setCredentialsProvider(credentialsProvider)
            .build()
        )
      )(_ => /*IO(p.shutdown()) >>*/ IO.unit) // TODO: shutdown properly. Somehow this hangs the publisher unit test
      subscription = ProjectSubscriptionName.of(projectTopicName.getProject, projectTopicName.getTopic)
      dispatcher <- Dispatcher[IO]
      receiver = GoogleSubscriberInterpreter.receiver(queue, dispatcher)
      sub <- Resource.eval(
        IO(
          Subscriber
            .newBuilder(subscription, receiver) // TODO: set credentials correctly
            .setChannelProvider(channelProvider)
            .setCredentialsProvider(credentialsProvider)
            .build()
        )
      )
    } yield {
      // create topic
      Try(topicClient.createTopic(projectTopicName)).void.recover[Unit] { case e: io.grpc.StatusRuntimeException =>
        if (e.getStatus.getCode == Code.ALREADY_EXISTS)
          ()
        else
          throw e
      }

      // create subscription
      Try(
        subscriptionAdminClient.createSubscription(subscription, projectTopicName, PushConfig.getDefaultInstance, 10)
      ).void
        .recover[Unit] { case e: io.grpc.StatusRuntimeException =>
          if (e.getStatus.getCode == Code.ALREADY_EXISTS)
            ()
          else
            throw e
        }

      (GooglePublisherInterpreter[IO](pub), GoogleSubscriberInterpreter[IO, A](sub, queue))
    }
}

final case class Person(name: String, email: String)
