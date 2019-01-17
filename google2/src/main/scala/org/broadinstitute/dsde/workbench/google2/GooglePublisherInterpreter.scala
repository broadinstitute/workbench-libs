package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.implicits._
import com.google.api.core.ApiFutures
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1.{Publisher, TopicAdminClient, TopicAdminSettings}
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{ProjectTopicName, PubsubMessage}
import com.google.rpc.Code
import fs2.Sink
import io.circe.Encoder
import io.circe.syntax._

private[google2] class GooglePublisherInterpreter[F[_]: Async: Timer: ContextShift](
                                                         publisher: Publisher,
                                                         topicAdminClient: TopicAdminClient
                                     ) extends GooglePublisher[F] {
  def publish[A: Encoder]: Sink[F, A] = Sink {
    a =>
      val byteString = ByteString.copyFromUtf8(a.asJson.toString()) //This will turn a case class into raw json string
      val message = PubsubMessage.newBuilder().setData(byteString).build()
      Async[F].async[String]{
        cb =>
          ApiFutures.addCallback(
            publisher.publish(message),
            callBack(cb),
            MoreExecutors.directExecutor()
          )
      }.void
  }
}

object GooglePublisherInterpreter {
  def apply[F[_]: Async: Timer: ContextShift](
             publisher: Publisher,
             topicAdminClient: TopicAdminClient
           ): GooglePublisherInterpreter[F] = new GooglePublisherInterpreter(publisher, topicAdminClient)

  def publisher[F[_]: Sync](pathToJson: String, projectTopicName: ProjectTopicName): Resource[F, Publisher] =
    for{
      credentialFile <- org.broadinstitute.dsde.workbench.util.readFile(pathToJson)
      credential = ServiceAccountCredentials.fromStream(credentialFile)
      pub <- Resource.make(
        Sync[F].delay(Publisher
          .newBuilder(projectTopicName)
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
          .build()))(p => Sync[F].delay(p.shutdown()))
      topicAdminClient <- Resource.make(
        Sync[F].delay(TopicAdminClient.create(
          TopicAdminSettings
            .newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credential))
            .build()))
      )(client => Sync[F].delay(client.shutdown()))
      _ <- Resource.liftF(
        Sync[F]
        .delay(topicAdminClient.createTopic(projectTopicName))
        .void
        .recover {
          case e: io.grpc.StatusRuntimeException if(e.getStatus.getCode == Code.ALREADY_EXISTS) => ()
        })
    } yield pub
}
