package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.mtl.Ask
import cats.syntax.all._
import com.google.api.core.ApiFutures
import com.google.api.gax.core.{FixedCredentialsProvider, FixedExecutorProvider}
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1.{Publisher, TopicAdminClient, TopicAdminSettings}
import com.google.common.util.concurrent.{MoreExecutors, ThreadFactoryBuilder}
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{ProjectTopicName, PubsubMessage, TopicName}
import fs2.{Pipe, Stream}
import io.circe.Encoder
import io.circe.syntax._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.messaging.CloudPublisher
import org.broadinstitute.dsde.workbench.util2.{tracedLogging, withLogging}
import org.typelevel.log4cats.StructuredLogger

import java.util.concurrent.ScheduledThreadPoolExecutor
import scala.jdk.CollectionConverters.MapHasAsJava

private[google2] class GooglePublisherInterpreter[F[_]: Async: StructuredLogger](
  publisher: Publisher
) extends GooglePublisher[F]
    with CloudPublisher[F] {
  def publish[MessageType: Encoder]: Pipe[F, MessageType, Unit] = in =>
    in.flatMap { message =>
      Stream
        .eval(publishMessage(message.asJson.noSpaces, None)) // This will turn message case class into raw json string
    }

  def publishNative: Pipe[F, PubsubMessage, Unit] = in =>
    in.flatMap { message =>
      Stream.eval(
        publishNativeOne(message)
      )
    }

  /**
   * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
   */
  override def publishNativeOne(message: PubsubMessage): F[Unit] = withLogging(
    Async[F]
      .async[String] { callback =>
        Async[F]
          .delay(
            ApiFutures.addCallback(
              publisher.publish(message),
              callBack(callback),
              MoreExecutors.directExecutor()
            )
          )
          .as(None)
      }
      .void,
    Option(message.getAttributesMap.get("traceId")).map(s => TraceId(s)),
    s"Publishing $message"
  )

  def publishString: Pipe[F, String, Unit] = in => in.flatMap(s => Stream.eval(publishMessage(s, None)))

  override def publishOne[MessageType: Encoder](message: MessageType)(implicit ev: Ask[F, TraceId]): F[Unit] = {
    val byteString = ByteString.copyFromUtf8(message.asJson.noSpaces)
    tracedLogging(asyncPublishMessage(byteString), s"com.google.cloud.pubsub.v1.Publisher.publish($byteString)")
  }
  override def publishOne[MessageType: Encoder](message: MessageType,
                                                messageAttributes: Map[String, String] = Map.empty
  )(implicit ev: Ask[F, TraceId]): F[Unit] = {
    val byteString = ByteString.copyFromUtf8(message.asJson.noSpaces)
    tracedLogging(asyncPublishMessage(byteString, messageAttributes),
                  s"com.google.cloud.pubsub.v1.Publisher.publish($byteString)"
    )
  }

  private def publishMessage(message: String, traceId: Option[TraceId]): F[Unit] = {
    val byteString = ByteString.copyFromUtf8(message)
    withLogging(asyncPublishMessage(byteString), traceId, s"Publishing $message")
  }

  private def asyncPublishMessage(byteString: ByteString, attributes: Map[String, String] = Map.empty): F[Unit] =
    Async[F]
      .async[String] { callback =>
        val message = PubsubMessage
          .newBuilder()
          .setData(byteString)
          .putAllAttributes(attributes.asJava)
          .build()
        Async[F]
          .delay(
            ApiFutures.addCallback(
              publisher.publish(message),
              callBack(callback),
              MoreExecutors.directExecutor()
            )
          )
          .as(None)
      }
      .void
}

object GooglePublisherInterpreter {
  def apply[F[_]: Async: StructuredLogger](
    publisher: Publisher
  ): GooglePublisherInterpreter[F] = new GooglePublisherInterpreter(publisher)

  def publisher[F[_]: Async: StructuredLogger](config: PublisherConfig,
                                               numOfThreads: Int = 20
  ): Resource[F, Publisher] =
    for {
      credential <- credentialResource(config.pathToCredentialJson)
      publisher <- publisherResource(config.projectTopicName, credential, numOfThreads)
      topicAdminClient <- GoogleTopicAdminInterpreter.topicAdminClientResource(credential)
      _ <- createTopic(config.projectTopicName, topicAdminClient)
    } yield publisher

  private def createTopic[F[_]: Sync](topicName: TopicName, topicAdminClient: TopicAdminClient)(implicit
    logger: StructuredLogger[F]
  ): Resource[F, Unit] =
    Resource.eval(
      Sync[F]
        .delay(topicAdminClient.createTopic(topicName))
        .void
        .recoverWith { case _: AlreadyExistsException =>
          logger.info(s"$topicName already exists")
        }
    )

  private def publisherResource[F[_]: Sync](topicName: ProjectTopicName,
                                            credential: ServiceAccountCredentials,
                                            numOfThreads: Int
  ): Resource[F, Publisher] = {
    val threadFactory = new ThreadFactoryBuilder().setNameFormat("goog-publisher-%d").setDaemon(true).build()
    val fixedExecutorProvider =
      FixedExecutorProvider.create(new ScheduledThreadPoolExecutor(numOfThreads, threadFactory))
    // Using TopicAdminSettings here since there aren't equivalent publisher settings and its being used with the topicAdmin
    val transportChannelProvider =
      TopicAdminSettings.defaultTransportChannelProvider().withExecutor(fixedExecutorProvider.getExecutor)

    Resource.make(
      Sync[F].delay(
        Publisher
          .newBuilder(topicName)
          .setExecutorProvider(fixedExecutorProvider)
          .setCredentialsProvider(FixedCredentialsProvider.create(credential))
          .setChannelProvider(transportChannelProvider)
          .build()
      )
    )(p => Sync[F].delay(p.shutdown()))
  }
}

final case class PublisherConfig(pathToCredentialJson: String, projectTopicName: ProjectTopicName)
