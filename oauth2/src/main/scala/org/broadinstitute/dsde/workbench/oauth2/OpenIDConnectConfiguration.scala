package org.broadinstitute.dsde.workbench.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.{FormData, HttpRequest, StatusCodes, Uri => AkkaUri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import cats.effect.Async
import cats.syntax.all._
import io.circe.Decoder
import org.http4s.Uri
import org.http4s.blaze.client._
import org.http4s.circe.CirceEntityDecoder._

/**
 * Allows services to configure their mode of OAuth by providing 2 backend routes:
 * `/oauth2/authorize` and `/oauth2/token`.
 *
 * To use this class, first instantiate by calling `OpenIDConnectConfiguration.apply`
 * with the following options:
 *   - authorityEndpoint (required): the OAuth2.0 authority, e.g. https://accounts.google.com
 *   - oidcClientId (required): the OAuth2 client id
 *   - oidcClientSecret (optional): the OAuth2 client secret. Only needed for Google, not B2C.
 *   - extraAuthParams (optional): if present appends extra params to the query string of the
 *       authorization request. This is needed for B2C for some clients, including Swagger UI.
 *   - extraGoogleClientId (optional): if present adds a Google-specific client to Swagger UI
 *       with implicit flow. Used for backwards compatiblity.
 *
 * There are 2 choices for using this class:
 *
 *   1. If your service is using akka-http, you can generate an akka-http route using
 *      `toAkkaHttpRoute` and add it directly to your service. Additionally `serviceSwaggerUiIndex`
 *      can be used to serve Swagger UI index.html with the right oauth fields populated.
 *      Note: ensure the service is using a compatible akka-http version with the version
 *      workbench-libs is compiled against.
 *
 *   2. Otherwise, the service should add 2 backend routes as follows:
 *     - GET /oauth2/authorize:
 *         This route should call `processAuthorizeQueryParams` on the incoming querystring params
 *         and redirect to the endpoint returned by `getAuthorizationEndpoint`.
 *     - POST /oauth2/token:
 *         This route should only accept Content-Type: application/x-www-form-urlencoded.
 *         It should call `processTokenFormFields` on the incoming form fields and _proxy_ the request
 *         to the endpoint returned by `getTokenEndpoint`.
 */
trait OpenIDConnectConfiguration {
  def getAuthorizationEndpoint: String
  def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)]

  def getTokenEndpoint: String
  def processTokenFormFields(fields: Seq[(String, String)]): Seq[(String, String)]

  def processSwaggerUiIndex(content: String): String
}

object OpenIDConnectConfiguration {
  private val oidcMetadataUrlSuffix = ".well-known/openid-configuration"

  def apply[F[_]: Async](authorityEndpoint: String,
                         oidcClientId: ClientId,
                         oidcClientSecret: Option[ClientSecret] = None,
                         extraAuthParams: Option[String] = None,
                         extraGoogleClientId: Option[ClientId] = None
  ) = for {
    metadata <- getProviderMetadata(authorityEndpoint)
  } yield new OpenIDConnectInterpreter(metadata, oidcClientId, oidcClientSecret, extraAuthParams, extraGoogleClientId)

  // Grabs the authorize and token endpoints from the authority metadata JSON
  private[oauth2] def getProviderMetadata[F[_]: Async](authorityEndpoint: String): F[OpenIDProviderMetadata] =
    BlazeClientBuilder[F].resource.use { client =>
      val req = Uri.unsafeFromString(authorityEndpoint + "/" + oidcMetadataUrlSuffix)
      client.expectOr[OpenIDProviderMetadata](req)(onError =>
        Async[F].raiseError(
          new RuntimeException(s"Error reading OIDC configuration endpoint: ${onError.status.reason}")
        )
      )
    }

  final class OpenIDConnectConfigurationOps(private val config: OpenIDConnectConfiguration) extends AnyVal {
    def toAkkaHttpRoute(implicit actorSystem: ActorSystem): Route =
      pathPrefix("oauth2") {
        path("authorize") {
          get {
            parameterSeq { params =>
              val newQuery = AkkaUri.Query(config.processAuthorizeQueryParams(params): _*)
              val newUri = AkkaUri(config.getAuthorizationEndpoint).withQuery(newQuery)
              redirect(newUri, StatusCodes.Found)
            }
          }
        } ~
          path("token") {
            post {
              formFieldSeq { fields =>
                complete {
                  val newRequest = HttpRequest(
                    POST,
                    uri = AkkaUri(config.getTokenEndpoint),
                    entity = FormData(config.processTokenFormFields(fields): _*).toEntity
                  )
                  Http().singleRequest(newRequest)
                }
              }
            }
          }
      }

    def serveSwaggerUiIndex: Route =
      mapResponseEntity { entityFromJar =>
        entityFromJar.transformDataBytes(Flow.fromFunction { original =>
          ByteString(config.processSwaggerUiIndex(original.utf8String))
        })
      } {
        getFromResource("swagger/index.html")
      }
  }

  implicit private val openIDProviderMetadataDecoder: Decoder[OpenIDProviderMetadata] = Decoder.instance { x =>
    for {
      issuer <- x.downField("issuer").as[String]
      authorizationEndpoint <- x.downField("authorization_endpoint").as[String]
      tokenEndpoint <- x.downField("token_endpoint").as[String]
    } yield OpenIDProviderMetadata(issuer, authorizationEndpoint, tokenEndpoint)
  }

  implicit def openIDConnectConfigurationOps(config: OpenIDConnectConfiguration): OpenIDConnectConfigurationOps =
    new OpenIDConnectConfigurationOps(config)
}

case class ClientId(value: String) extends AnyVal
case class ClientSecret(value: String) extends AnyVal
case class OpenIDProviderMetadata(issuer: String, authorizeEndpoint: String, tokenEndpoint: String) {
  def isGoogle: Boolean = issuer == "https://accounts.google.com"
}
