package org.broadinstitute.dsde.workbench.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import cats.effect.Async
import cats.syntax.all._
import io.circe.Decoder
import org.http4s.Uri
import org.http4s.blaze.client._
import org.http4s.circe.CirceEntityDecoder._

trait OpenIDConnectConfiguration {
  def toAkkaHttpRoute(implicit actorSystem: ActorSystem): Route
}

object OpenIDConnectConfiguration {
  private val oidcMetadataUrlSuffix = ".well-known/openid-configuration"

  def apply[F[_]: Async](authorityEndpoint: String,
                         clientId: String,
                         clientSecret: Option[String] = None,
                         extraAuthParams: Map[String, String] = Map.empty,
                         addClientIdToScope: Boolean = false
  ): F[OpenIDConnectConfiguration] = for {
    metadata <- getProviderMetadata(authorityEndpoint)
  } yield new OpenIDConnectInterpreter(metadata, clientId, clientSecret, extraAuthParams, addClientIdToScope)

  private[oauth2] def getProviderMetadata[F[_]: Async](authorityEndpoint: String): F[OpenIDProviderMetadata] =
    BlazeClientBuilder[F].resource.use { client =>
      val req = Uri.unsafeFromString(authorityEndpoint + "/" + oidcMetadataUrlSuffix)
      client.expectOr[OpenIDProviderMetadata](req)(onError =>
        Async[F].raiseError(
          new RuntimeException(s"Error reading OIDC configuration endpoint: ${onError.status.reason}")
        )
      )
    }

  implicit private val openIDProviderMetadataDecoder: Decoder[OpenIDProviderMetadata] = Decoder.instance { x =>
    for {
      authorizationEndpoint <- x.downField("authorization_endpoint").as[String]
      tokenEndpoint <- x.downField("token_endpoint").as[String]
    } yield OpenIDProviderMetadata(authorizationEndpoint, tokenEndpoint)
  }
}

case class OpenIDProviderMetadata(authorizeEndpoint: String, tokenEndpoint: String)
