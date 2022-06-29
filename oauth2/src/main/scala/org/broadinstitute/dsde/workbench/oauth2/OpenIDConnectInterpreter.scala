package org.broadinstitute.dsde.workbench.oauth2

import akka.http.scaladsl.model.Uri
import org.broadinstitute.dsde.workbench.oauth2.OpenIDConnectConfiguration.policyParam

class OpenIDConnectInterpreter private[oauth2] (val clientId: ClientId,
                                                val authorityEndpoint: String,
                                                val providerMetadata: OpenIDProviderMetadata,
                                                clientSecret: Option[ClientSecret],
                                                extraAuthParams: Option[String],
                                                extraGoogleClientId: Option[ClientId]
) extends OpenIDConnectConfiguration {
  private val scopeParam = "scope"
  private val clientSecretParam = "client_secret"

  override def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)] = {
    val paramsWithScope = if (!providerMetadata.isGoogle) {
      params.map { case (k, v) =>
        if (k == scopeParam) (k, v + " " + clientId.value) else (k, v)
      }
    } else params

    val paramsWithScopeAndExtraAuthParams =
      paramsWithScope ++ extraAuthParams.map(eap => Uri.Query(eap)).getOrElse(Uri.Query.Empty)

    paramsWithScopeAndExtraAuthParams
  }

  override def processTokenFormFields(fields: Seq[(String, String)]): Seq[(String, String)] =
    clientSecret match {
      case Some(secret) =>
        if (providerMetadata.isGoogle && !fields.exists(_._1 == clientSecretParam))
          fields :+ (clientSecretParam -> secret.value)
        else fields
      case None => fields
    }

  override def processSwaggerUiIndex(contents: String, openApiYamlPath: String): String =
    contents
      .replace("url: ''", s"url: '$openApiYamlPath'")
      .replace("googleoauth: ''", s"googleoauth: '${extraGoogleClientId.map(_.value).getOrElse("")}'")
      .replace("oidc: ''", s"oidc: '${clientId.value}'")
}
