package org.broadinstitute.dsde.workbench.google2
import cats.effect.{IO, Resource}
import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1.{TopicAdminClient, TopicAdminSettings}
import com.google.pubsub.v1.ProjectTopicName
import fs2.Stream
import io.grpc.ManagedChannelBuilder
import org.broadinstitute.dsde.workbench.google2.Generators._
import org.broadinstitute.dsde.workbench.google2.GoogleTopicAdminSpec._
import org.broadinstitute.dsde.workbench.util2.{PropertyBasedTesting, WorkbenchTestSuite}
import org.scalatest.{FlatSpec, Matchers}

class GoogleTopicAdminSpec extends FlatSpec with Matchers with WorkbenchTestSuite with PropertyBasedTesting {
  "GoogleTopicAdminInterpreter" should "be able to create topic" in {
    forAll { (topic: ProjectTopicName) =>
      val result = localTopicAdmin.use { topicAdmin =>
        val googleTopicAdmin =
          new GoogleTopicAdminInterpreter[IO](topicAdmin, GoogleTopicAdminInterpreter.defaultRetryConfig)
        val res = for {
          _ <- googleTopicAdmin.create(topic)
          t <- Stream.eval(IO(topicAdmin.getTopic(topic)))
        } yield t.getName shouldBe topic.toString

        res.compile.lastOrError
      }

      result.unsafeRunSync()
    }
  }

  "GoogleTopicAdminInterpreter" should "be able to delete topic" in {
    forAll { (topic: ProjectTopicName) =>
      val result = localTopicAdmin.use { topicAdmin =>
        val googleTopicAdmin =
          new GoogleTopicAdminInterpreter[IO](topicAdmin, GoogleTopicAdminInterpreter.defaultRetryConfig)
        val res = for {
          _ <- googleTopicAdmin.create(topic)
          _ <- googleTopicAdmin.delete(topic)
          caught = the[com.google.api.gax.rpc.NotFoundException] thrownBy {
            topicAdmin.getTopic(topic)
          }
        } yield (caught.getMessage should include("NOT_FOUND"))

        res.compile.lastOrError
      }

      result.unsafeRunSync()
    }
  }

  //pubsub getIamPolicy isn't implemented in emulator
}

object GoogleTopicAdminSpec {
  val localTopicAdmin: Resource[IO, TopicAdminClient] = for {
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
  } yield topicClient
}
