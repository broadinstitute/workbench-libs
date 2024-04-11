package org.broadinstitute.dsde.workbench.oauth2

import akka.http.scaladsl.model.Uri

class OpenIDConnectInterpreter private[oauth2] (val clientId: ClientId,
                                                val authorityEndpoint: String,
                                                val providerMetadata: OpenIDProviderMetadata,
                                                extraAuthParams: Option[String],
                                                b2cProfileWithGoogleBillingScope: Option[String] = None
) extends OpenIDConnectConfiguration {
  private val scopeParam = "scope"

  override def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)] = {
    val paramsWithScope = params.map { case (k, v) =>
      if (k == scopeParam) (k, v + " " + clientId.value) else (k, v)
    }

    val paramsWithScopeAndExtraAuthParams =
      paramsWithScope ++ extraAuthParams.map(eap => Uri.Query(eap)).getOrElse(Uri.Query.Empty)

    paramsWithScopeAndExtraAuthParams
  }

  override def processSwaggerUiIndex(contents: String, openApiYamlPath: String): String =
    contents
      .replace("url: ''", s"url: '$openApiYamlPath'")
      .replace("oidc: ''", s"oidc: '${clientId.value}'")
      .replace("oidc_google_billing_scope: ''", s"oidc_google_billing_scope: '${clientId.value}'")

    override def processOpenApiYaml(contents: String): String =
      b2cProfileWithGoogleBillingScope.map { b2cProfile =>
        contents.replace("B2C_PROFILE_WITH_GOOGLE_BILLING_SCOPE", b2cProfile)
      }.getOrElse(contents)
}
