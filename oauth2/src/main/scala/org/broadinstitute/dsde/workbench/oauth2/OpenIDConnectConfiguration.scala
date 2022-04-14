package org.broadinstitute.dsde.workbench.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import cats.effect.{Async, Resource}
import io.circe.Decoder
import org.http4s.Uri
import org.http4s.blaze.client._
import org.http4s.circe.CirceEntityDecoder._

trait OpenIDConnectConfiguration[F[_]] {
  def toAkkaHttpRoute(implicit actorSystem: ActorSystem): Route
}

object OpenIDConnectConfiguration {
  private val oidcMetadataUrlSuffix = ".well-known/openid-configuration"

  def resource[F[_]](authorityEndpoint: String,
                     clientId: String,
                     clientSecret: Option[String] = None,
                     extraAuthParams: Map[String, String] = Map.empty,
                     addClientIdToScope: Boolean = false
  )(implicit
    F: Async[F]
  ): Resource[F, OpenIDConnectInterpreter[F]] = for {
    metadata <- getProviderMetadata(authorityEndpoint)
  } yield new OpenIDConnectInterpreter(metadata, clientId, clientSecret, extraAuthParams, addClientIdToScope)

  private[oauth2] def getProviderMetadata[F[_]: Async](authorityEndpoint: String): Resource[F, OpenIDProviderMetadata] =
    for {
      client <- BlazeClientBuilder[F].resource
      req = Uri.unsafeFromString(authorityEndpoint + "/" + oidcMetadataUrlSuffix)
      result <- Resource.eval(
        client.expectOr[OpenIDProviderMetadata](req)(onError =>
          Async[F].raiseError(
            new RuntimeException(s"Error reading OIDC configuration endpoint: ${onError.status.reason}")
          )
        )
      )
    } yield result

  implicit private val openIDProviderMetadataDecoder: Decoder[OpenIDProviderMetadata] = Decoder.instance { x =>
    for {
      authorizationEndpoint <- x.downField("authorization_endpoint").as[String]
      tokenEndpoint <- x.downField("token_endpoint").as[String]
    } yield OpenIDProviderMetadata(authorizationEndpoint, tokenEndpoint)
  }
}

case class OpenIDProviderMetadata(authorizeEndpoint: String, tokenEndpoint: String)
