package org.broadinstitute.dsde.workbench.oauth2.mock

import org.broadinstitute.dsde.workbench.oauth2.{ClientId, OpenIDConnectConfiguration, OpenIDProviderMetadata, OpenIdProvider}
import org.http4s.Uri

class FakeOpenIDConnectConfiguration extends OpenIDConnectConfiguration {
  override def clientId: ClientId = ClientId("some-client")

  override def openIdProvider: OpenIdProvider = OpenIdProvider(authorityEndpoint, Uri.fromString("https://fake/.well-known/openid-configuration").toOption.get, providerMetadata)

  val authorityEndpoint: String = "https://fake"

  val providerMetadata: OpenIDProviderMetadata =
    OpenIDProviderMetadata("fake-issuer", "fake-authorize", "fake-token", Option("fake-end-session"))

  override def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)] = params

  override def processSwaggerUiIndex(contents: String, openApiFileName: String): String = contents

  override def processOpenApiYaml(contents: String): String = contents
}

object FakeOpenIDConnectConfiguration extends FakeOpenIDConnectConfiguration
