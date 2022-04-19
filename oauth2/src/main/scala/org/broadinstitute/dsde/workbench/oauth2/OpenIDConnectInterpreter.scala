package org.broadinstitute.dsde.workbench.oauth2

import akka.http.scaladsl.model.Uri

import scala.io.Source

class OpenIDConnectInterpreter private[oauth2] (providerMetadata: OpenIDProviderMetadata,
                                                oidcClientId: ClientId,
                                                oidcClientSecret: Option[ClientSecret],
                                                extraAuthParams: Option[String],
                                                extraGoogleClientId: Option[ClientId]
) extends OpenIDConnectConfiguration {
  private val scopeParam = "scope"
  private val clientSecretParam = "client_secret"

  override def getAuthorizationEndpoint: String = providerMetadata.authorizeEndpoint

  override def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)] = {
    val paramsWithScope = if (!providerMetadata.isGoogle) {
      params.map { case (k, v) =>
        if (k == scopeParam) (k, v + " " + oidcClientId.value) else (k, v)
      }
    } else params

    val paramsWithScopeAndExtraAuthParams =
      paramsWithScope ++ extraAuthParams.map(eap => Uri.Query(eap)).getOrElse(Uri.Query.Empty)

    paramsWithScopeAndExtraAuthParams
  }

  override def getTokenEndpoint: String = providerMetadata.tokenEndpoint

  override def processTokenFormFields(fields: Seq[(String, String)]): Seq[(String, String)] =
    oidcClientSecret match {
      case Some(secret) =>
        if (providerMetadata.isGoogle && !fields.exists(_._1 == clientSecretParam))
          fields :+ (clientSecretParam -> secret.value)
        else fields
      case None => fields
    }

  override def getSwaggerUiIndex(openApiYamlPath: String): String = {
    val source = Source.fromResource("swagger/index.html")
    try
      source.mkString
        .replace("url: ''", s"url: '$openApiYamlPath'")
        .replace("googleoauth: ''", s"googleoauth: '${extraGoogleClientId.map(_.value).getOrElse("")}'")
        .replace("oidc: ''", s"oidc: '${oidcClientId.value}'")
    finally
      source.close()
  }
}
