package org.broadinstitute.dsde.workbench.oauth2

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
 *   - extraAuthParams (optional): if present appends extra params to the query string of the
 *       authorization request. This is needed for B2C for some clients, including Swagger UI.
 *
 * There are 2 choices for using this class:
 *
 *   1. If your service is using akka-http, you can generate akka-http routes using the
 *      `OpenIDConnectAkkaHttpOps` class and add them directly to your service.
 *      Note: ensure the service is using a compatible akka-http version with the version
 *      workbench-libs is compiled against.
 *
 *   2. Otherwise, the service should add 2 backend routes as follows:
 *     - GET /oauth2/authorize:
 *         This route should call `processAuthorizeQueryParams` on the incoming querystring params
 *         and redirect to the authorize endpoint defined in `providerMetadata`.
 *     - POST /oauth2/token:
 *         This route should only accept Content-Type: application/x-www-form-urlencoded.
 *         It should call `processTokenFormFields` on the incoming form fields and _proxy_ the request
 *         to the token endpoint defined in `providerMetadata`.
 */
trait OpenIDConnectConfiguration {
  def clientId: ClientId
  def openIdProvider: OpenIdProvider

  def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)]
  def processSwaggerUiIndex(contents: String, openApiFileName: String): String
  def processOpenApiYaml(contents: String): String
}

object OpenIDConnectConfiguration {
  private val oidcMetadataUrlSuffix = ".well-known/openid-configuration"

  def apply[F[_]: Async](authorityEndpoint: String,
                         oidcClientId: ClientId,
                         extraAuthParams: Option[String] = None,
                         authorityEndpointWithGoogleBillingScope: Option[String] = None
  ): F[OpenIDConnectConfiguration] = for {
    openIdProvider <- getOpenIdProvider(authorityEndpoint)
    openIdProviderWithGoogleBillingScope <- authorityEndpointWithGoogleBillingScope.traverse(
      getOpenIdProvider[F]
    )
  } yield new OpenIDConnectInterpreter(oidcClientId,
                                       openIdProvider,
                                       extraAuthParams,
                                       openIdProviderWithGoogleBillingScope
  )

  private[oauth2] def getOpenIdProvider[F[_]: Async](authorityEndpoint: String): F[OpenIdProvider] =
    for {
      metadataUri <- getProviderMetadataUri(authorityEndpoint)
      metadata <- getProviderMetadata(metadataUri)
    } yield OpenIdProvider(authorityEndpoint, metadataUri, metadata)

  private[oauth2] def getProviderMetadataUri[F[_]: Async](authorityEndpoint: String): F[Uri] =
    Async[F].fromEither(Uri.fromString(authorityEndpoint)).map(_.addPath(oidcMetadataUrlSuffix))

  // Grabs the authorize and token endpoints from the authority metadata JSON
  private[oauth2] def getProviderMetadata[F[_]: Async](providerMetadataUri: Uri): F[OpenIDProviderMetadata] =
    for {
      resp <- BlazeClientBuilder[F].resource.use { client =>
        client.expectOr[OpenIDProviderMetadata](providerMetadataUri)(onError =>
          Async[F].raiseError(
            new RuntimeException(s"Error reading OIDC configuration endpoint: ${onError.status.reason}")
          )
        )
      }
    } yield resp

  implicit private val openIDProviderMetadataDecoder: Decoder[OpenIDProviderMetadata] = Decoder.instance { x =>
    for {
      issuer <- x.downField("issuer").as[String]
      authorizationEndpoint <- x.downField("authorization_endpoint").as[String]
      tokenEndpoint <- x.downField("token_endpoint").as[String]
      endSessionEndpoint <- x.downField("end_session_endpoint").as[Option[String]]
    } yield OpenIDProviderMetadata(issuer, authorizationEndpoint, tokenEndpoint, endSessionEndpoint);
  }
  implicit def openIDConnectConfigurationOps(config: OpenIDConnectConfiguration): OpenIDConnectAkkaHttpOps =
    new OpenIDConnectAkkaHttpOps(config)
}

case class ClientId(value: String) extends AnyVal
case class OpenIDProviderMetadata(issuer: String,
                                  authorizeEndpoint: String,
                                  tokenEndpoint: String,
                                  endSessionEndpoint: Option[String]
)
case class OpenIdProvider(authorityEndpoint: String, metadataUri: Uri, metadata: OpenIDProviderMetadata)
