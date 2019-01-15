package org.broadinstitute.dsde.workbench.google2


import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1._
import com.google.pubsub.v1.{ProjectSubscriptionName, ProjectTopicName, PubsubMessage, PushConfig}
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.circe.Decoder
import io.circe.generic.auto._
import io.grpc.ManagedChannelBuilder
import io.grpc.Status.Code
import org.broadinstitute.dsde.workbench.google2.GooglePubSubSpec._
import org.broadinstitute.dsde.workbench.util.WorkbenchTest
import org.scalatest.{FlatSpec, Matchers}
import io.circe.parser._

import scala.concurrent.duration._
import scala.util.Try

class GooglePubSubSpec extends FlatSpec with Matchers with WorkbenchTest {
  "GooglePublisherInterpreter" should "be able to publish message successfully" in ioAssertion {
    val person = Generators.genPerson.sample.get
    val projectTopicName = Generators.genProjectTopicName.sample.get

    val handler: Person => IO[Unit] = _ => IO.unit //dummy handler for subscriber since we don't care about subscriber in this test
    localPubsub(projectTopicName, handler).use{
      case (pub, _) =>
        val source = Stream(person)

        val res = source to pub.publish[Person]

        res.compile.drain.as(succeed)
    }
  }

  "GoogleSubscriberInterpreter" should "be able to subscribe messages successfully" in {
    val person = Generators.genPerson.sample.get
    val projectTopicName = Generators.genProjectTopicName.sample.get

    val res = for {
      terminateSubscriber <- SignallingRef[IO, Boolean](false) //signal for terminating subscriber
      terminateStopStream <- SignallingRef[IO, Boolean](false) //signal for terminating stopStream
      messageHandler = {
        p: Person =>
          IO((p shouldBe person)) >> terminateSubscriber.set(true)
      }
      _ <- localPubsub(projectTopicName, messageHandler).use {
        case (pub, sub) =>
          val source = Stream(person)
          val subScribeStream = (source to pub.publish[Person]) ++ Stream.eval(sub.start)
          // stopStream will check every 1 seconds to see if SignallingRef is set to false, if so terminate subscriber
          val stopStream: Stream[IO, Unit] = (Stream.sleep(1 seconds) ++ Stream.eval_{
            for {
              r <- terminateSubscriber.get
              _ <- if(r) {
                sub.stop >> terminateStopStream.set(true)
              } else IO.unit
            } yield ()
          }).repeat.interruptWhen(terminateStopStream)

          val finalStream = Stream(subScribeStream, stopStream).parJoinUnbounded
          finalStream.compile.drain
      }
    } yield succeed

    res.unsafeRunSync()
  }
}

object GooglePubSubSpec {
  def localPubsub[A: Decoder](projectTopicName: ProjectTopicName, handler: A => IO[Unit])(implicit timer: Timer[IO], cs: ContextShift[IO]): Resource[IO, (GooglePublisherInterpreter[IO], GoogleSubscriberInterpreter[IO])] = {
    for{
      channel <- Resource.make(IO(ManagedChannelBuilder.forTarget("localhost:8085").usePlaintext().build()))(c => IO(c.shutdown()))
      channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel))
      credentialsProvider = NoCredentialsProvider.create()
      topicClient <- Resource.make(IO(TopicAdminClient.create(
        TopicAdminSettings.newBuilder()
          .setTransportChannelProvider(channelProvider)
          .setCredentialsProvider(credentialsProvider)
          .build())))(client => IO(client.shutdown()))
      subscriptionAdminClient <- Resource.make(IO(SubscriptionAdminClient.create(
        SubscriptionAdminSettings.newBuilder()
          .setTransportChannelProvider(channelProvider)
          .setCredentialsProvider(credentialsProvider)
          .build())))(client => IO(client.shutdown()))
      pub <- Resource.make(IO(Publisher.newBuilder(projectTopicName)
        .setChannelProvider(channelProvider)
        .setCredentialsProvider(credentialsProvider)
        .build()))(p => IO(p.shutdown()))
      subscription = ProjectSubscriptionName.of(projectTopicName.getProject, projectTopicName.getTopic)
      receiver = new MessageReceiver() {
        override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
          val result = for {
            json <- parse(message.getData.toStringUtf8)
            a <- json.as[A]
            _ <- handler(a).attempt.unsafeRunSync()
          } yield {
            consumer.ack()
          }

          result.leftMap(throw _)
        }
      }
      sub <- Resource.make(
        IO(Subscriber
          .newBuilder(subscription, receiver) //TODO: set credentials correctly
          .setChannelProvider(channelProvider)
          .setCredentialsProvider(credentialsProvider)
          .build())
      )(s => IO(s.stopAsync()))
      publisher = GooglePublisherInterpreter[IO](pub, topicClient)
      _ <- Resource.liftF(publisher.createTopic(projectTopicName))
    } yield {
      // create subscription
      Try(subscriptionAdminClient.createSubscription(subscription, projectTopicName, PushConfig.getDefaultInstance, 10)).as(()).recover[Unit]{
        case e: io.grpc.StatusRuntimeException =>
          if(e.getStatus.getCode == Code.ALREADY_EXISTS)
            ()
          else
            throw e
      }

      (publisher, GoogleSubscriberInterpreter[IO](sub))
    }
  }
}

final case class Person(name: String, email: String)