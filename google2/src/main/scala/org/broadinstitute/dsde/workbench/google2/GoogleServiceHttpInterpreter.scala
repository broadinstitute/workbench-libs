package org.broadinstitute.dsde.workbench
package google2

import cats.Eq
import cats.data.NonEmptyList
import cats.effect.{Resource, Sync}
import cats.syntax.all._
import com.google.api.services.storage.StorageScopes
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.google.cloud.Identity
import com.google.pubsub.v1.TopicName
import io.chrisdavenport.log4cats.Logger
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.broadinstitute.dsde.workbench.google2.GoogleServiceHttpInterpreter._
import org.broadinstitute.dsde.workbench.google2.JsonCodec._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Authorization
import JsonCodec._
import org.http4s.{AuthScheme, Credentials, Headers, Method, Request, Response, Status, Uri}

class GoogleServiceHttpInterpreter[F[_]: Sync: Logger](httpClient: Client[F],
                                                       config: NotificationCreaterConfig,
                                                       googleCredentials: GoogleCredentials
) extends GoogleServiceHttp[F]
    with Http4sClientDsl[F] {
  val authHeader = Authorization(
    Credentials.Token(AuthScheme.Bearer, googleCredentials.refreshAccessToken().getTokenValue)
  )

  // Google api doc https://cloud.google.com/storage/docs/json_api/v1/notifications
  def createNotification(topic: TopicName,
                         bucketName: GcsBucketName,
                         filters: Filters,
                         traceId: Option[TraceId]
  ): F[Unit] = {
    val notificationUri = config.googleUrl.withPath(s"/storage/v1/b/${bucketName.value}/notificationConfigs")
    val notificationBody = NotificationRequest(topic, "JSON_API_V1", filters.eventTypes, filters.objectNamePrefix)
    val headers = Headers.of(authHeader)

    for {
      notifications <- httpClient.expect[NotificationResponse](
        Request[F](
          method = Method.GET,
          uri = notificationUri,
          headers = headers
        )
      )
      // POST request will create multiple notifications for same bucket and topic with different ID; Hence we check if
      // a notification for the given bucket and topic already exists. If yes, we do nothing; else, we create it
      _ <-
        if (notifications.items.exists(nel => nel.toList.exists(x => x.topic === topic)))
          Sync[F].pure(())
        else
          httpClient.expectOr[Notification](
            Request[F](
              method = Method.POST,
              uri = notificationUri,
              headers = headers
            ).withEntity(notificationBody)
          )(onError(traceId))
    } yield ()
  }

  // https://cloud.google.com/storage/docs/getting-service-account
  def getProjectServiceAccount(project: GoogleProject, traceId: Option[TraceId]): F[Identity] = {
    val uri = config.googleUrl.withPath(s"/storage/v1/projects/${project.value}/serviceAccount")
    httpClient
      .expectOr[GetProjectServiceAccountResponse](
        Request[F](
          method = Method.GET,
          uri = uri,
          headers = Headers.of(authHeader)
        )
      )(onError(traceId))
      .map(_.serviceAccount)
  }

  private def onError(traceId: Option[TraceId]): Response[F] => F[Throwable] = resp => {
    for {
      body <- (resp.body through fs2.text.utf8Decode).compile.foldMonoid
      errorResponse = LoggableErrorResponse(traceId, resp.status, body)
      _ <- Logger[F].warn(errorResponse.asJson.noSpaces)
    } yield new RuntimeException(errorResponse.asJson.noSpaces)
  }
}

object GoogleServiceHttpInterpreter {
  implicit val notificationEventTypesEncoder: Encoder[NotificationEventTypes] =
    Encoder.encodeString.contramap(eventType => eventType.asString)

  implicit val notificationRequestEncoder: Encoder[NotificationRequest] = Encoder.forProduct4(
    "topic",
    "payload_format",
    "event_types",
    "object_name_prefix"
  )(x => NotificationRequest.unapply(x).get)

  implicit val notificationDecoder: Decoder[Notification] = Decoder.forProduct1(
    "topic"
  )(Notification.apply)

  implicit val notificationResponseDecoder: Decoder[NotificationResponse] = Decoder.forProduct1(
    "items"
  )(NotificationResponse.apply)

  implicit val statusEncoder: Encoder[Status] = Encoder.encodeInt.contramap(_.code)

  implicit def entityBodyEncoder: Encoder[LoggableErrorResponse] =
    Encoder.forProduct3(
      "traceId",
      "status",
      "error"
    ) { x =>
      (x.traceId, x.status, x.body)
    }

  implicit def identityDeocder: Decoder[Identity] = Decoder.decodeString.map(s => Identity.serviceAccount(s))

  implicit def getProjectServiceAccountResponse: Decoder[GetProjectServiceAccountResponse] =
    Decoder.forProduct1(
      "email_address"
    )(GetProjectServiceAccountResponse)

  implicit val eqTopicName: Eq[TopicName] =
    Eq.instance((t1, t2) => t1.getProject == t2.getProject && t1.getTopic == t2.getTopic)

  def credentialResourceWithScope[F[_]: Sync](pathToCredential: String): Resource[F, GoogleCredentials] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential)
      credential <- Resource.liftF(
        Sync[F].delay(ServiceAccountCredentials.fromStream(credentialFile).createScoped(StorageScopes.all()))
      )
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
final private[google2] case class NotificationRequest(topic: TopicName,
                                                      payloadFormat: String,
                                                      eventTypes: List[NotificationEventTypes],
                                                      objectNamePrefix: Option[String]
)
final private[google2] case class NotificationResponse(items: Option[NonEmptyList[Notification]])
final case class Notification(topic: TopicName)
final case class NotificationCreaterConfig(pathToCredentialJson: String, googleUrl: Uri)
final case class LoggableErrorResponse(traceId: Option[TraceId], status: Status, body: String)
final case class GetProjectServiceAccountResponse(serviceAccount: Identity)
