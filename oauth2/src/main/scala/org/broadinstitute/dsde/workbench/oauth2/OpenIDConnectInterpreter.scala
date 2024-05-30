package org.broadinstitute.dsde.workbench.oauth2

import akka.http.scaladsl.model
import org.http4s.Uri

class OpenIDConnectInterpreter private[oauth2] (val clientId: ClientId,
                                                val openIdProvider: OpenIdProvider,
                                                extraAuthParams: Option[String],
                                                openIdProviderWithGoogleBillingScope: Option[OpenIdProvider] = None
) extends OpenIDConnectConfiguration {
  private val scopeParam = "scope"

  override def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)] = {
    val paramsWithScope = params.map { case (k, v) =>
      if (k == scopeParam) (k, v + " " + clientId.value) else (k, v)
    }

    val paramsWithScopeAndExtraAuthParams =
      paramsWithScope ++ extraAuthParams.map(eap => model.Uri.Query(eap)).getOrElse(model.Uri.Query.Empty)

    paramsWithScopeAndExtraAuthParams
  }

  override def processSwaggerUiIndex(contents: String, openApiYamlPath: String): String =
    contents
      .replace("url: ''", s"url: '$openApiYamlPath'")
      .replace("clientId: ''", s"clientId: '${clientId.value}'")

  override def processOpenApiYaml(contents: String): String = {
    // important to replace the urls with suffixes first, then the urls without suffixes
    // to avoid replacing the urls with suffixes with the urls without suffixes
    val providersWithSuffix =
      openIdProviderWithGoogleBillingScope.map((_, "_WITH_GOOGLE_BILLING_SCOPE")) ++
        Some((openIdProvider, ""))

    providersWithSuffix.foldLeft(contents) { case (acc, (provider, suffix)) =>
      replaceUrls(acc, provider, suffix)
    }
  }

  private def replaceUrls(contents: String, provider: OpenIdProvider, suffix: String): String =
    contents
      .replace("OPEN_ID_CONNECT_URL" + suffix, provider.metadataUri.toString())
      .replace("OAUTH_AUTHORIZATION_URL" + suffix, provider.metadata.authorizeEndpoint)
      .replace("OAUTH_TOKEN_URL" + suffix, provider.metadata.tokenEndpoint)
}
