package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.implicits._
import com.google.api.core.ApiFutures
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1.{Publisher, TopicAdminClient, TopicAdminSettings}
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{ProjectTopicName, PubsubMessage}
import fs2.Sink
import io.circe.Encoder
import io.circe.syntax._
import org.broadinstitute.dsde.workbench.RetryConfig

private[google2] class GooglePublisherInterpreter[F[_]: Async: Timer](
                                                         publisher: Publisher,
                                                         retryConfig: RetryConfig
                                     ) extends GooglePublisher[F] {
  def publish[A: Encoder]: Sink[F, A] = in => {
    in.flatMap{
      a =>
        val byteString = ByteString.copyFromUtf8(a.asJson.noSpaces) //This will turn a case class into raw json string
        retryGoogleF(retryConfig)(asyncPublishMessage(byteString))
    }
  }

  def publishString: Sink[F, String] = in => {
    in.flatMap{
      str =>
        val byteString = ByteString.copyFromUtf8(str)
        retryGoogleF(retryConfig)(asyncPublishMessage(byteString))
    }
  }

  private def asyncPublishMessage(byteString: ByteString): F[Unit] = Async[F].async[String]{
    cb =>
      val message = PubsubMessage.newBuilder().setData(byteString).build()
      ApiFutures.addCallback(
        publisher.publish(message),
        callBack(cb),
        MoreExecutors.directExecutor()
      )
  }.void
}

object GooglePublisherInterpreter {
  def apply[F[_]: Async: Timer: ContextShift](
             publisher: Publisher,
             retryConfig: RetryConfig
           ): GooglePublisherInterpreter[F] = new GooglePublisherInterpreter(publisher, retryConfig)

  def publisher[F[_]: Sync](config: PublisherConfig): Resource[F, Publisher] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util.readFile(config.pathToCredentialJson)
      credential = ServiceAccountCredentials.fromStream(credentialFile)
      publisher <- publisherResource(config.projectTopicName, credential)
      topicAdminClient <- topicAdminClientResource(credential)
      _ <- createTopic(config.projectTopicName, topicAdminClient)
    } yield publisher

  private def createTopic[F[_] : Sync](topicName: ProjectTopicName, topicAdminClient: TopicAdminClient) = {
    Resource.liftF(
      Sync[F]
        .delay(topicAdminClient.createTopic(topicName))
        .void
        .recover {
          case _: AlreadyExistsException => ()
        })
  }

  private def topicAdminClientResource[F[_] : Sync](credential: ServiceAccountCredentials) = {
    Resource.make(
      Sync[F].delay(TopicAdminClient.create(
        TopicAdminSettings
          .newBuilder()
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
          .build()))
    )(client => Sync[F].delay(client.shutdown()))
  }

  private def publisherResource[F[_] : Sync](topicName: ProjectTopicName, credential: ServiceAccountCredentials) = {
    Resource.make(
      Sync[F].delay(Publisher
        .newBuilder(topicName)
        .setCredentialsProvider(FixedCredentialsProvider.create(credential))
        .build()))(p => Sync[F].delay(p.shutdown()))
  }
}

final case class PublisherConfig(pathToCredentialJson: String, projectTopicName: ProjectTopicName, retryConfig: RetryConfig)