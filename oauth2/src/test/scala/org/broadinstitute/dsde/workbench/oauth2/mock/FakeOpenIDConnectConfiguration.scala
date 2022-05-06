package org.broadinstitute.dsde.workbench.oauth2.mock

import org.broadinstitute.dsde.workbench.oauth2.OpenIDConnectConfiguration

class FakeOpenIDConnectConfiguration extends OpenIDConnectConfiguration {
  override def getAuthorizationEndpoint: String = "some-auth-provider"

  override def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)] = params

  override def getTokenEndpoint: String = "some-token-endpoint"

  override def processTokenFormFields(fields: Seq[(String, String)]): Seq[(String, String)] = fields

  override def processSwaggerUiIndex(contents: String, openApiFileName: String): String = contents
}

object FakeOpenIDConnectConfiguration extends FakeOpenIDConnectConfiguration
