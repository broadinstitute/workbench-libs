package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Resource, Sync, Timer}
import cats.syntax.all._
import cats.mtl.Ask
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.Identity
import com.google.cloud.pubsub.v1.{TopicAdminClient, TopicAdminSettings}
import com.google.iam.v1.{Binding, GetIamPolicyRequest, Policy, SetIamPolicyRequest}
import com.google.pubsub.v1.{ProjectName, Topic, TopicName}
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.collection.JavaConverters._

class GoogleTopicAdminInterpreter[F[_]: StructuredLogger: Timer](topicAdminClient: TopicAdminClient)(implicit
  F: Sync[F]
) extends GoogleTopicAdmin[F] {
  def create(projectTopicName: TopicName, traceId: Option[TraceId] = None): F[Unit] = {
    val loggingCtx = Map("topic" -> projectTopicName.toString, "traceId" -> traceId.map(_.asString).getOrElse(""))
    val createTopic = F.delay(topicAdminClient.createTopic(projectTopicName)).void.recoverWith {
      case _: com.google.api.gax.rpc.AlreadyExistsException =>
        StructuredLogger[F].debug(loggingCtx)(s"$projectTopicName already exists")
    }

    withLogging(createTopic, traceId, s"com.google.cloud.pubsub.v1.TopicAdminClient.createTopic($projectTopicName)")
  }

  def delete(projectTopicName: TopicName, traceId: Option[TraceId] = None): F[Unit] = {
    val deleteTopic = F.delay(topicAdminClient.deleteTopic(projectTopicName))

    withLogging(deleteTopic, traceId, s"com.google.cloud.pubsub.v1.TopicAdminClient.deleteTopic($projectTopicName)")
  }

  override def list(project: GoogleProject)(implicit ev: Ask[F, TraceId]): Stream[F, Topic] = {
    val listTopics = F.delay(topicAdminClient.listTopics(ProjectName.of(project.value)))

    for {
      resp <- Stream.eval(
        tracedLogging(listTopics, s"com.google.cloud.pubsub.v1.TopicAdminClient.listTopics(${project})")
      )
      pagedResponse <- Stream.fromIterator(resp.iteratePages().iterator().asScala)
      topics <- Stream.fromIterator(pagedResponse.getValues.iterator().asScala)
    } yield topics
  }

  def createWithPublisherMembers(topicName: TopicName,
                                 members: List[Identity],
                                 traceId: Option[TraceId] = None
  ): F[Unit] = {
    val loggingCtx = Map("topic" -> topicName.toString, "traceId" -> traceId.map(_.asString).getOrElse(""))
    val createTopic = Sync[F].delay(topicAdminClient.createTopic(topicName)).void.recoverWith {
      case _: com.google.api.gax.rpc.AlreadyExistsException =>
        StructuredLogger[F].debug(loggingCtx)(s"$topicName topic already exists")
    }

    for {
      _ <- withLogging(createTopic, traceId, s"com.google.cloud.pubsub.v1.TopicAdminClient.createTopic($topicName)")

      topicNameString = TopicName.format(topicName.getProject, topicName.getTopic)
      request = GetIamPolicyRequest.newBuilder().setResource(topicNameString).build()
      getPolicy = Sync[F].delay(
        topicAdminClient.getIamPolicy(request)
      ) //getIamPolicy requires `roles/pubsub.admin` role, see https://cloud.google.com/pubsub/docs/access-control

      policy <- withLogging(getPolicy, traceId, s"com.google.cloud.pubsub.v1.TopicAdminClient.getIamPolicy($topicName)")
      binding = Binding
        .newBuilder()
        .setRole("roles/pubsub.publisher")
        .addAllMembers(members.map(_.strValue()).asJava) //strValue has right format binding expects
        .build()
      updatedPolicy = Policy.newBuilder(policy).addBindings(binding).build()

      request = SetIamPolicyRequest
        .newBuilder()
        .setResource(topicNameString)
        .setPolicy(updatedPolicy)
        .build()
      updatePolicy = Sync[F].delay(topicAdminClient.setIamPolicy(request)).void
      _ <- withLogging(updatePolicy,
                       traceId,
                       s"com.google.cloud.pubsub.v1.TopicAdminClient.setIamPolicy($topicName, $updatedPolicy)"
      )
    } yield ()
  }
}

object GoogleTopicAdminInterpreter {
  private[google2] def topicAdminClientResource[F[_]: Sync](
    credential: ServiceAccountCredentials
  ): Resource[F, TopicAdminClient] =
    Resource.make(
      Sync[F].delay(
        TopicAdminClient.create(
          TopicAdminSettings
            .newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credential))
            .build()
        )
      )
    )(client => Sync[F].delay(client.shutdown()))
}
