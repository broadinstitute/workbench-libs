package org.broadinstitute.dsde.workbench.oauth2.mock

import org.broadinstitute.dsde.workbench.oauth2.{ClientId, OpenIDConnectConfiguration, OpenIDProviderMetadata}

class FakeOpenIDConnectConfiguration extends OpenIDConnectConfiguration {
  override def clientId: ClientId = ClientId("some-client")

  override def authorityEndpoint: String = "https://fake"

  override def providerMetadata: OpenIDProviderMetadata =
    OpenIDProviderMetadata("fake-issuer", "fake-authorize", "fake-token")

  override def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)] = params

  override def processTokenFormFields(fields: Seq[(String, String)]): Seq[(String, String)] = fields

  override def processSwaggerUiIndex(contents: String, openApiFileName: String): String = contents
}

object FakeOpenIDConnectConfiguration extends FakeOpenIDConnectConfiguration
