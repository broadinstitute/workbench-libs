package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Resource, Sync, Timer}
import cats.implicits._
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.Identity
import com.google.cloud.pubsub.v1.{TopicAdminClient, TopicAdminSettings}
import com.google.iam.v1.{Binding, Policy}
import com.google.pubsub.v1.ProjectTopicName
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.RetryConfig

import scala.concurrent.duration._
import collection.JavaConverters._
import fs2.Stream
import io.circe.syntax._
import JsonCodec._
import org.broadinstitute.dsde.workbench.model.TraceId

class GoogleTopicAdminInterpreter[F[_]: Logger: Sync: Timer](topicAdminClient: TopicAdminClient, retryConfig: RetryConfig) extends GoogleTopicAdmin[F] {
  def retryHelper[A]: (F[A], Option[TraceId], String) => Stream[F, A] = retryGoogleF[F, A](retryConfig)

  def create(projectTopicName: ProjectTopicName, traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val loggingCtx = Map("topic" -> projectTopicName.asJson, "traceId" -> traceId.asJson)
    val createTopic = Sync[F].delay(topicAdminClient.createTopic(projectTopicName)).void.handleErrorWith {
        case _: com.google.api.gax.rpc.AlreadyExistsException =>
          Logger[F].debug(s"$projectTopicName already exists")
      }

    retryHelper[Unit](createTopic, traceId, s"com.google.cloud.pubsub.v1.TopicAdminClient.createTopic($projectTopicName)")
  }

  def createWithPublisherMembers(projectTopicName: ProjectTopicName, members: List[Identity], traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val loggingCtx = Map("topic" -> projectTopicName.asJson, "traceId" -> traceId.asJson)
    val createTopic = Sync[F].delay(topicAdminClient.createTopic(projectTopicName)).void.handleErrorWith {
      case _: com.google.api.gax.rpc.AlreadyExistsException =>
        Logger[F].debug(s"$projectTopicName topic already exists")
    }

    for {
      _ <- retryHelper[Unit](createTopic, traceId,  s"com.google.cloud.pubsub.v1.TopicAdminClient.createTopic($projectTopicName)")

      topicName = ProjectTopicName.format(projectTopicName.getProject, projectTopicName.getTopic)
      getPolicy = Sync[F].delay(topicAdminClient.getIamPolicy(topicName)) //getIamPolicy requires `roles/pubsub.admin` role, see https://cloud.google.com/pubsub/docs/access-control

      policy <- retryHelper[Policy](getPolicy, traceId, s"com.google.cloud.pubsub.v1.TopicAdminClient.getIamPolicy($topicName)")
      binding = Binding.newBuilder()
        .setRole("roles/pubsub.publisher")
        .addAllMembers(members.map(_.strValue()).asJava) //strValue has right format binding expects
        .build()
      updatedPolicy = Policy.newBuilder(policy).addBindings(binding).build()

      updatePolicy = Sync[F].delay(topicAdminClient.setIamPolicy(topicName, updatedPolicy)).void
      _ <- retryHelper[Unit](updatePolicy, traceId, s"com.google.cloud.pubsub.v1.TopicAdminClient.setIamPolicy($topicName, $updatedPolicy)")
    } yield ()
  }
}

object GoogleTopicAdminInterpreter {
  val defaultRetryConfig = RetryConfig(
    org.broadinstitute.dsde.workbench.util2.addJitter(1 seconds, 1 seconds),
    x => x * 2,
    5
  )

  private[google2] def topicAdminClientResource[F[_] : Sync](credential: ServiceAccountCredentials): Resource[F, TopicAdminClient] = {
    Resource.make(
      Sync[F].delay(TopicAdminClient.create(
        TopicAdminSettings
          .newBuilder()
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
          .build()))
    )(client => Sync[F].delay(client.shutdown()))
  }
}
