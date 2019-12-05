package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.implicits._
import com.google.api.core.ApiFutures
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1.{Publisher, TopicAdminClient}
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{ProjectTopicName, PubsubMessage}
import fs2.Pipe
import io.chrisdavenport.log4cats.Logger
import io.circe.Encoder
import io.circe.syntax._
import org.broadinstitute.dsde.workbench.RetryConfig

private[google2] class GooglePublisherInterpreter[F[_]: Async: Timer: Logger](
                                                                               publisher: Publisher,
                                                                               retryConfig: RetryConfig
                                                                             ) extends GooglePublisher[F] {
  def publish[MessageType: Encoder]: Pipe[F, MessageType, Unit] = in => {
    in.flatMap {
      message =>
        publishMessage(message.asJson.noSpaces) //This will turn message case class into raw json string
    }
  }

  def publishString: Pipe[F, String, Unit] = in => {
    in.flatMap(publishMessage)
  }

  private def publishMessage(message: String) = {
    val byteString = ByteString.copyFromUtf8(message)
    retryGoogleF(retryConfig)(asyncPublishMessage(byteString), None, s"Publishing $message")
  }

  private def asyncPublishMessage(byteString: ByteString): F[Unit] = Async[F].async[String]{
    callback =>
      val message = PubsubMessage.newBuilder().setData(byteString).build()
      ApiFutures.addCallback(
        publisher.publish(message),
        callBack(callback),
        MoreExecutors.directExecutor()
      )
  }.void
}

object GooglePublisherInterpreter {
  def apply[F[_]: Async: Timer: ContextShift: Logger](
                                                       publisher: Publisher,
                                                       retryConfig: RetryConfig
                                                     ): GooglePublisherInterpreter[F] = new GooglePublisherInterpreter(publisher, retryConfig)

  def publisher[F[_]: Sync](config: PublisherConfig): Resource[F, Publisher] =
    for {
      credential <- credentialResource(config.pathToCredentialJson)
      publisher <- publisherResource(config.projectTopicName, credential)
      topicAdminClient <- GoogleTopicAdminInterpreter.topicAdminClientResource(credential)
      _ <- createTopic(config.projectTopicName, topicAdminClient)
    } yield publisher

  private def createTopic[F[_] : Sync](topicName: ProjectTopicName, topicAdminClient: TopicAdminClient): Resource[F, Unit] = {
    Resource.liftF(
      Sync[F]
        .delay(topicAdminClient.createTopic(topicName))
        .void
        .recover {
          case _: AlreadyExistsException => ()
        })
  }

  private def publisherResource[F[_] : Sync](topicName: ProjectTopicName, credential: ServiceAccountCredentials): Resource[F, Publisher] = {
    Resource.make(
      Sync[F].delay(Publisher
        .newBuilder(topicName)
        .setCredentialsProvider(FixedCredentialsProvider.create(credential))
        .build()))(p => Sync[F].delay(p.shutdown()))
  }
}

final case class PublisherConfig(pathToCredentialJson: String, projectTopicName: ProjectTopicName, retryConfig: RetryConfig)