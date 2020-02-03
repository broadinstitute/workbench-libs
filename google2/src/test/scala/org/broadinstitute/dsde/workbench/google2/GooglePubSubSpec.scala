package org.broadinstitute.dsde.workbench.google2

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1._
import com.google.pubsub.v1.{ProjectSubscriptionName, ProjectTopicName, PushConfig}
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.chrisdavenport.log4cats.Logger
import io.circe.Decoder
import io.circe.generic.auto._
import io.grpc.ManagedChannelBuilder
import io.grpc.Status.Code
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.GooglePubSubSpec._
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.util.Try

class GooglePubSubSpec extends FlatSpec with Matchers with WorkbenchTestSuite {
  "GooglePublisherInterpreter" should "be able to publish message successfully" in {
    val people = Generators.genListPerson.sample.get
    val projectTopicName = Generators.genProjectTopicName.sample.get

    val res = for {
      queue <- fs2.concurrent.Queue.bounded[IO, Event[Person]](10000)
      _ <- localPubsub[Person](projectTopicName, queue).use {
        case (pub, _) =>
          val res = Stream.emits(people) through pub.publish

          res.compile.drain
      }
    } yield succeed

    res.unsafeRunSync()
  }

  "GoogleSubscriberInterpreter" should "be able to subscribe messages successfully" in {
    val people = Generators.genListPerson.sample.get
    val projectTopicName = Generators.genProjectTopicName.sample.get

    var expectedPeople = people
    val res = for {
      queue <- fs2.concurrent.Queue.bounded[IO, Event[Person]](10000)
      terminateSubscriber <- SignallingRef[IO, Boolean](false) //signal for terminating subscriber
      terminateStopStream <- SignallingRef[IO, Boolean](false) //signal for terminating stopStream
      _ <- localPubsub(projectTopicName, queue).use {
        case (pub, sub) =>
          val subScribeStream = (Stream.emits(people) through pub.publish[Person]) ++ Stream.eval(sub.start)

          val processEvents: Stream[IO, Unit] = sub.messages.zipWithIndex
            .evalMap[IO, Unit] {
              case (event, index) =>
                if (expectedPeople.contains(event.msg)) {
                  expectedPeople = expectedPeople.filterNot(_ == event.msg)
                  if (index.toInt == people.length - 1)
                    IO(event.consumer.ack()).void >> terminateSubscriber.set(true)
                  else
                    IO(event.consumer.ack()).void
                } else
                  IO.raiseError(new Exception(s"${event.msg} doesn't equal ${people(index.toInt)}")) >> terminateSubscriber
                    .set(true)
            }
            .interruptWhen(terminateStopStream)

          // stopStream will check every 1 seconds to see if SignallingRef is set to false, if so terminate subscriber
          val stopStream: Stream[IO, Unit] = (Stream.sleep(1 seconds) ++ Stream.eval_ {
            for {
              r <- terminateSubscriber.get
              _ <- if (r) {
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
  def localPubsub[A: Decoder](projectTopicName: ProjectTopicName, queue: fs2.concurrent.Queue[IO, Event[A]])(
    implicit timer: Timer[IO],
    cs: ContextShift[IO],
    logger: Logger[IO]
  ): Resource[IO, (GooglePublisherInterpreter[IO], GoogleSubscriberInterpreter[IO, A])] =
    for {
      channel <- Resource.make(IO(ManagedChannelBuilder.forTarget("localhost:8085").usePlaintext().build()))(
        c => IO(c.shutdown())
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
      )(p => /*IO(p.shutdown()) >>*/ IO.unit) //TODO: shutdown properly. Somehow this hangs the publisher unit test
      subscription = ProjectSubscriptionName.of(projectTopicName.getProject, projectTopicName.getTopic)
      receiver = GoogleSubscriberInterpreter.receiver(queue)
      sub <- Resource.liftF(
        IO(
          Subscriber
            .newBuilder(subscription, receiver) //TODO: set credentials correctly
            .setChannelProvider(channelProvider)
            .setCredentialsProvider(credentialsProvider)
            .build()
        )
      )
    } yield {
      // create topic
      Try(topicClient.createTopic(projectTopicName)).void.recover[Unit] {
        case e: io.grpc.StatusRuntimeException =>
          if (e.getStatus.getCode == Code.ALREADY_EXISTS)
            ()
          else
            throw e
      }

      // create subscription
      Try(subscriptionAdminClient.createSubscription(subscription, projectTopicName, PushConfig.getDefaultInstance, 10)).void
        .recover[Unit] {
          case e: io.grpc.StatusRuntimeException =>
            if (e.getStatus.getCode == Code.ALREADY_EXISTS)
              ()
            else
              throw e
        }

      (GooglePublisherInterpreter[IO](pub, RetryConfig(1 seconds, identity, 3, _ => true)),
       GoogleSubscriberInterpreter[IO, A](sub, queue))
    }
}

final case class Person(name: String, email: String)
