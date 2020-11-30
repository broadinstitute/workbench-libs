package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.syntax.all._
import cats.mtl.Ask
import com.google.api.core.ApiFutures
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1.{Publisher, TopicAdminClient}
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{ProjectTopicName, PubsubMessage, TopicName}
import fs2.{Pipe, Stream}
import io.chrisdavenport.log4cats.StructuredLogger
import io.circe.Encoder
import io.circe.syntax._
import org.broadinstitute.dsde.workbench.model.TraceId

private[google2] class GooglePublisherInterpreter[F[_]: Async: Timer: StructuredLogger](
  publisher: Publisher
) extends GooglePublisher[F] {
  def publish[MessageType: Encoder]: Pipe[F, MessageType, Unit] = in => {
    in.flatMap { message =>
      Stream
        .eval(publishMessage(message.asJson.noSpaces, None)) //This will turn message case class into raw json string
    }
  }

  def publishNative: Pipe[F, PubsubMessage, Unit] = in => {
    in.flatMap { message =>
      Stream.eval(
        withLogging(
          Async[F]
            .async[String] { callback =>
              ApiFutures.addCallback(
                publisher.publish(message),
                callBack(callback),
                MoreExecutors.directExecutor()
              )
            }
            .void,
          Option(message.getAttributesMap.get("traceId")).map(s => TraceId(s)),
          s"Publishing ${message}"
        )
      )
    }
  }

  def publishString: Pipe[F, String, Unit] = in => {
    in.flatMap(s => Stream.eval(publishMessage(s, None)))
  }

  override def publishOne[MessageType: Encoder](message: MessageType)(implicit ev: Ask[F, TraceId]): F[Unit] = {
    val byteString = ByteString.copyFromUtf8(message.asJson.noSpaces)
    tracedLogging(asyncPublishMessage(byteString), s"com.google.cloud.pubsub.v1.Publisher.publish($byteString)")
  }

  private def publishMessage(message: String, traceId: Option[TraceId]): F[Unit] = {
    val byteString = ByteString.copyFromUtf8(message)
    withLogging(asyncPublishMessage(byteString), traceId, s"Publishing $message")
  }

  private def asyncPublishMessage(byteString: ByteString): F[Unit] =
    Async[F]
      .async[String] { callback =>
        val message = PubsubMessage
          .newBuilder()
          .setData(byteString)
          .build()
        ApiFutures.addCallback(
          publisher.publish(message),
          callBack(callback),
          MoreExecutors.directExecutor()
        )
      }
      .void
}

object GooglePublisherInterpreter {
  def apply[F[_]: Async: Timer: ContextShift: StructuredLogger](
    publisher: Publisher
  ): GooglePublisherInterpreter[F] = new GooglePublisherInterpreter(publisher)

  def publisher[F[_]: Sync: StructuredLogger](config: PublisherConfig): Resource[F, Publisher] =
    for {
      credential <- credentialResource(config.pathToCredentialJson)
      publisher <- publisherResource(config.projectTopicName, credential)
      topicAdminClient <- GoogleTopicAdminInterpreter.topicAdminClientResource(credential)
      _ <- createTopic(config.projectTopicName, topicAdminClient)
    } yield publisher

  private def createTopic[F[_]: Sync](topicName: TopicName, topicAdminClient: TopicAdminClient)(implicit
    logger: StructuredLogger[F]
  ): Resource[F, Unit] =
    Resource.liftF(
      Sync[F]
        .delay(topicAdminClient.createTopic(topicName))
        .void
        .recoverWith { case _: AlreadyExistsException =>
          logger.info(s"${topicName} already exists")
        }
    )

  private def publisherResource[F[_]: Sync](topicName: ProjectTopicName,
                                            credential: ServiceAccountCredentials
  ): Resource[F, Publisher] =
    Resource.make(
      Sync[F].delay(
        Publisher
          .newBuilder(topicName)
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
          .build()
      )
    )(p => Sync[F].delay(p.shutdown()))
}

final case class PublisherConfig(pathToCredentialJson: String, projectTopicName: ProjectTopicName)
