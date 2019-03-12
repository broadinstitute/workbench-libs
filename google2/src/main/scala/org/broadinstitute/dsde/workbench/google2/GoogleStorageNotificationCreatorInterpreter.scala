package org.broadinstitute.dsde.workbench.google2

import cats.Eq
import cats.data.NonEmptyList
import cats.effect.{Resource, Sync}
import cats.implicits._
import com.google.api.services.storage.StorageScopes
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.google.pubsub.v1.ProjectTopicName
import io.circe.{Decoder, Encoder}
import org.broadinstitute.dsde.workbench.google2.GoogleStorageNotificationCreatorInterpreter._
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, Credentials, Headers, Method, Request, Uri}
import JsonCodec._

// Google api doc https://cloud.google.com/storage/docs/json_api/v1/notifications
class GoogleStorageNotificationCreatorInterpreter[F[_]: Sync](httpClient: Client[F], config: NotificationCreaterConfig) extends GoogleStorageNotificationCreater[F] with Http4sClientDsl[F] {
  def createNotification(topic: ProjectTopicName, bucketName: GcsBucketName, filters: Filters): F[Unit] = credentialResourceWithScope(config.pathToCredentialJson).use {
    serviceAccountCredentials =>
      val notificationUri = config.googleUrl.withPath(s"/storage/v1/b/${bucketName.value}/notificationConfigs")
      val notificationBody = NotificationRequest(topic, "JSON_API_V1", filters.eventTypes, filters.objectNamePrefix)
      val headers = Headers(Authorization(Credentials.Token(AuthScheme.Bearer, serviceAccountCredentials.refreshAccessToken().getTokenValue)))
      for {
        notifications <- httpClient.expect[NotificationResponse](Request[F](
          method = Method.GET,
          uri = notificationUri,
          headers = headers
        ))
        // POST request will create multiple notifications for same bucket and topic with different ID; Hence we check if
        // a notification for the given bucket and topic already exists. If yes, we do nothing; if no, we create it
      _ <- if(notifications.items.exists(nel => nel.toList.exists(x => x.topic === topic)))
          Sync[F].pure(())
        else httpClient.expect[Notification](Request[F](
          method = Method.POST,
          uri = notificationUri,
          headers = headers
        ).withEntity(notificationBody))
      } yield ()
  }
}

object GoogleStorageNotificationCreatorInterpreter {
  implicit val notificationEventTypesEncoder: Encoder[NotificationEventTypes] = Encoder.encodeString.contramap(eventType => eventType.asString)

  implicit val notificationRequestEncoder: Encoder[NotificationRequest] = Encoder.forProduct4(
    "topic",
    "payload_format",
    "event_types",
    "object_name_prefix"
  )(x => NotificationRequest.unapply(x).get)

  implicit val projectTopicNameDecoder: Decoder[ProjectTopicName] = Decoder.decodeString.emap { s =>
    // topic has this format: '//pubsub.googleapis.com/projects/{project-identifier}/topics/{my-topic}'
    Either.catchNonFatal(ProjectTopicName.parse(s)).leftMap(t => t.getMessage)
  }

  implicit val notificationDecoder: Decoder[Notification] = Decoder.forProduct1(
    "topic"
  )(Notification.apply)

  implicit val notificationResponseDecoder: Decoder[NotificationResponse] = Decoder.forProduct1(
    "items"
  )(NotificationResponse.apply)

  implicit val eqProjectTopicName: Eq[ProjectTopicName] = Eq.instance((t1, t2) => t1.getProject == t2.getProject && t1.getTopic == t2.getTopic)

  def credentialResourceWithScope[F[_]: Sync](pathToCredential: String): Resource[F, GoogleCredentials] = for {
    credentialFile <- org.broadinstitute.dsde.workbench.util.readFile(pathToCredential)
    credential <- Resource.liftF(Sync[F].delay(ServiceAccountCredentials.fromStream(credentialFile).createScoped(StorageScopes.all())))
  } yield credential
}

sealed abstract class NotificationEventTypes {
  def asString: String
}
object NotificationEventTypes {
  case object ObjectFinalize extends NotificationEventTypes {
    def asString: String = "OBJECT_FINALIZE"
  }
  case object ObjectMedataUpdate extends NotificationEventTypes {
    def asString: String = "OBJECT_METADATA_UPDATE"
  }
  case object ObjectDelete extends NotificationEventTypes {
    def asString: String = "OBJECT_DELETE"
  }
  case object ObjectArchive extends NotificationEventTypes {
    def asString: String = "OBJECT_ARCHIVE"
  }
}

final case class Filters(eventTypes: List[NotificationEventTypes], objectNamePrefix: Option[String])
private[google2] final case class NotificationRequest(topic: ProjectTopicName, payloadFormat: String, eventTypes: List[NotificationEventTypes], objectNamePrefix: Option[String])
private[google2] final case class NotificationResponse(items: Option[NonEmptyList[Notification]])
final case class Notification(topic: ProjectTopicName)
final case class NotificationCreaterConfig(pathToCredentialJson: String, googleUrl: Uri)