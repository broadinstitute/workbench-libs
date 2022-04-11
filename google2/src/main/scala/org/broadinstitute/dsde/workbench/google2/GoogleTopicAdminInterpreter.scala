package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import cats.syntax.all._
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.Identity
import com.google.cloud.pubsub.v1.{TopicAdminClient, TopicAdminSettings}
import com.google.iam.v1.{Binding, GetIamPolicyRequest, Policy, SetIamPolicyRequest}
import com.google.pubsub.v1.{ProjectName, Topic, TopicName}
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import scala.jdk.CollectionConverters._

class GoogleTopicAdminInterpreter[F[_]: StructuredLogger](topicAdminClient: TopicAdminClient)(implicit
  F: Async[F]
) extends GoogleTopicAdmin[F] {
  def create(projectTopicName: TopicName, traceId: Option[TraceId] = None): F[Unit] = {
    val loggingCtx = Map("topic" -> projectTopicName.toString, "traceId" -> traceId.map(_.asString).getOrElse(""))
    val createTopic = F.blocking(topicAdminClient.createTopic(projectTopicName)).void.recoverWith {
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
        tracedLogging(listTopics, s"com.google.cloud.pubsub.v1.TopicAdminClient.listTopics($project)")
      )
      pagedResponse <- Stream.fromIterator(resp.iteratePages().iterator().asScala, 1024)
      topics <- Stream.fromIterator(pagedResponse.getValues.iterator().asScala, 1024)
    } yield topics
  }

  def createWithPublisherMembers(topicName: TopicName,
                                 members: List[Identity],
                                 traceId: Option[TraceId] = None
  ): F[Unit] = {
    val loggingCtx = Map("topic" -> topicName.toString, "traceId" -> traceId.map(_.asString).getOrElse(""))
    val createTopic = F.blocking(topicAdminClient.createTopic(topicName)).void.recoverWith {
      case _: com.google.api.gax.rpc.AlreadyExistsException =>
        StructuredLogger[F].debug(loggingCtx)(s"$topicName topic already exists")
    }

    for {
      _ <- withLogging(createTopic, traceId, s"com.google.cloud.pubsub.v1.TopicAdminClient.createTopic($topicName)")

      topicNameString = TopicName.format(topicName.getProject, topicName.getTopic)
      request = GetIamPolicyRequest.newBuilder().setResource(topicNameString).build()
      getPolicy = F.blocking(
        topicAdminClient.getIamPolicy(request)
      ) // getIamPolicy requires `roles/pubsub.admin` role, see https://cloud.google.com/pubsub/docs/access-control

      policy <- withLogging(getPolicy, traceId, s"com.google.cloud.pubsub.v1.TopicAdminClient.getIamPolicy($topicName)")
      binding = Binding
        .newBuilder()
        .setRole("roles/pubsub.publisher")
        .addAllMembers(members.map(_.strValue()).asJava) // strValue has right format binding expects
        .build()
      updatedPolicy = Policy.newBuilder(policy).addBindings(binding).build()

      request = SetIamPolicyRequest
        .newBuilder()
        .setResource(topicNameString)
        .setPolicy(updatedPolicy)
        .build()
      updatePolicy = F.blocking(topicAdminClient.setIamPolicy(request)).void
      _ <- withLogging(updatePolicy,
                       traceId,
                       s"com.google.cloud.pubsub.v1.TopicAdminClient.setIamPolicy($topicName, $updatedPolicy)"
      )
    } yield ()
  }
}

object GoogleTopicAdminInterpreter {
  private[google2] def topicAdminClientResource[F[_]: Async](
    credential: ServiceAccountCredentials
  ): Resource[F, TopicAdminClient] =
    Resource.make(
      Async[F].delay(
        TopicAdminClient.create(
          TopicAdminSettings
            .newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credential))
            .build()
        )
      )
    )(client => Async[F].delay(client.shutdown()))
}
