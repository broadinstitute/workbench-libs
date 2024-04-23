package org.broadinstitute.dsde.workbench.oauth2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.http4s.Uri
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.io.Source

class OpenIDConnectConfigurationSpec extends AnyFlatSpecLike with Matchers with WorkbenchTestSuite {
  val fakeMetadata = OpenIDProviderMetadata("issuer", "authorize", "token", Option("endSession"))

  "OpenIDConnectConfiguration" should "initialize with B2C metadata" in {
    val res = for {
      metadata <- OpenIDConnectConfiguration.getProviderMetadata[IO](
        Uri.unsafeFromString("https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin_dev")
      )
    } yield {
      metadata.issuer should startWith(
        "https://terradevb2c.b2clogin.com/"
      )
      metadata.authorizeEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/authorize"
      metadata.tokenEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/token"
      metadata.endSessionEndpoint shouldBe Option(
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/logout"
      )
    }
    res.unsafeRunSync
  }

  it should "initialize with B2C metadata using query string" in {
    val res = for {
      metadata <- OpenIDConnectConfiguration.getProviderMetadata[IO](
        Uri.unsafeFromString("https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/v2.0/.well-known/openid-configuration?p=b2c_1a_signup_signin_dev")
      )
    } yield {
      metadata.issuer should startWith(
        "https://terradevb2c.b2clogin.com/"
      )
      metadata.authorizeEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/oauth2/v2.0/authorize?p=b2c_1a_signup_signin_dev"
      metadata.tokenEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/oauth2/v2.0/token?p=b2c_1a_signup_signin_dev"
      metadata.endSessionEndpoint shouldBe Option(
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/oauth2/v2.0/logout?p=b2c_1a_signup_signin_dev"
      )
    }
    res.unsafeRunSync
  }

  "processAuthorizeQueryParams" should "inject the client_id to the scope" in {
    val interp = new OpenIDConnectInterpreter(ClientId("client_id"), "fake-authority", Uri(), fakeMetadata, None)

    val params = List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile")
    val res = interp.processAuthorizeQueryParams(params)

    res shouldBe List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile client_id")
  }

  it should "inject the client_id and extra auth params" in {
    val interp =
      new OpenIDConnectInterpreter(ClientId("client_id"), "fake-authority", Uri(), fakeMetadata, Some("extra=1&fields=more"))

    val params = List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile")
    val res = interp.processAuthorizeQueryParams(params)

    res shouldBe List("foo" -> "bar",
                      "abc" -> "123",
                      "scope" -> "openid email profile client_id",
                      "extra" -> "1",
                      "fields" -> "more"
    )
  }

  "processSwaggerUiIndex" should "replace client ids and uri" in {
    val interp =
      new OpenIDConnectInterpreter(ClientId("client_id"), "fake-authority", Uri(), fakeMetadata, None)
    val source = Source.fromResource("swagger/index.html")
    val contents =
      try source.mkString
      finally source.close()
    val res = interp.processSwaggerUiIndex(contents, "/api-docs.yaml")
    res should include(
      """  var clientIds = {
        |    oidc: 'client_id',
        |    oidc_google_billing_scope: 'client_id'
        |  }""".stripMargin
    )
    res should include("url: '/api-docs.yaml'")
  }
}
