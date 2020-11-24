package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Resource, Sync, Timer}
import cats.implicits._
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.Identity
import com.google.cloud.pubsub.v1.{TopicAdminClient, TopicAdminSettings}
import com.google.iam.v1.{Binding, GetIamPolicyRequest, Policy, SetIamPolicyRequest}
import com.google.pubsub.v1.TopicName
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class GoogleTopicAdminInterpreter[F[_]: StructuredLogger: Sync: Timer](topicAdminClient: TopicAdminClient,
                                                                       retryConfig: RetryConfig
) extends GoogleTopicAdmin[F] {
  def retryHelper[A]: (F[A], Option[TraceId], String) => Stream[F, A] = retryGoogleF[F, A](retryConfig)

  def create(projectTopicName: TopicName, traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val loggingCtx = Map("topic" -> projectTopicName.toString, "traceId" -> traceId.map(_.asString).getOrElse(""))
    val createTopic = Sync[F].delay(topicAdminClient.createTopic(projectTopicName)).void.recoverWith {
      case _: com.google.api.gax.rpc.AlreadyExistsException =>
        StructuredLogger[F].debug(loggingCtx)(s"$projectTopicName already exists")
    }

    retryHelper[Unit](createTopic,
                      traceId,
                      s"com.google.cloud.pubsub.v1.TopicAdminClient.createTopic($projectTopicName)"
    )
  }

  def delete(projectTopicName: TopicName, traceId: Option[TraceId] = None): Stream[F, Unit] = {
    val deleteTopic = Sync[F].delay(topicAdminClient.deleteTopic(projectTopicName))

    retryHelper[Unit](deleteTopic,
                      traceId,
                      s"com.google.cloud.pubsub.v1.TopicAdminClient.deleteTopic($projectTopicName)"
    )
  }

  def createWithPublisherMembers(topicName: TopicName,
                                 members: List[Identity],
                                 traceId: Option[TraceId] = None
  ): Stream[F, Unit] = {
    val loggingCtx = Map("topic" -> topicName.toString, "traceId" -> traceId.map(_.asString).getOrElse(""))
    val createTopic = Sync[F].delay(topicAdminClient.createTopic(topicName)).void.recoverWith {
      case _: com.google.api.gax.rpc.AlreadyExistsException =>
        StructuredLogger[F].debug(loggingCtx)(s"$topicName topic already exists")
    }

    for {
      _ <- retryHelper[Unit](createTopic,
                             traceId,
                             s"com.google.cloud.pubsub.v1.TopicAdminClient.createTopic($topicName)"
      )

      topicNameString = TopicName.format(topicName.getProject, topicName.getTopic)
      request = GetIamPolicyRequest.newBuilder().setResource(topicNameString).build()
      getPolicy = Sync[F].delay(
        topicAdminClient.getIamPolicy(request)
      ) //getIamPolicy requires `roles/pubsub.admin` role, see https://cloud.google.com/pubsub/docs/access-control

      policy <- retryHelper[Policy](getPolicy,
                                    traceId,
                                    s"com.google.cloud.pubsub.v1.TopicAdminClient.getIamPolicy($topicName)"
      )
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
      _ <- retryHelper[Unit](updatePolicy,
                             traceId,
                             s"com.google.cloud.pubsub.v1.TopicAdminClient.setIamPolicy($topicName, $updatedPolicy)"
      )
    } yield ()
  }
}

object GoogleTopicAdminInterpreter {
  val defaultRetryConfig = RetryConfig(
    org.broadinstitute.dsde.workbench.util2.addJitter(1 seconds, 1 seconds),
    x => x * 2,
    5
  )

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
