package org.broadinstitute.dsde.workbench.oauth2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.io.Source

class OpenIDConnectConfigurationSpec extends AnyFlatSpecLike with Matchers with WorkbenchTestSuite {
  val fakeMetadata = OpenIDProviderMetadata("issuer", "authorize", "token", Option("endSession"))
  val googleMetadata = OpenIDProviderMetadata("https://accounts.google.com", "authorize", "token", Option.empty)

  "OpenIDConnectConfiguration" should "initialize with Google metadata" in {
    val res = for {
      metadata <- OpenIDConnectConfiguration.getProviderMetadata[IO]("https://accounts.google.com")
    } yield {
      metadata.issuer shouldBe "https://accounts.google.com"
      metadata.authorizeEndpoint shouldBe "https://accounts.google.com/o/oauth2/v2/auth"
      metadata.tokenEndpoint shouldBe "https://oauth2.googleapis.com/token"
      metadata.endSessionEndpoint shouldBe Option.empty
    }
    res.unsafeRunSync
  }

  it should "initialize with B2C metadata" in {
    val res = for {
      metadata <- OpenIDConnectConfiguration.getProviderMetadata[IO](
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin"
      )
    } yield {
      metadata.issuer should startWith(
        "https://terradevb2c.b2clogin.com/"
      )
      metadata.authorizeEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/authorize"
      metadata.tokenEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/token"
      metadata.endSessionEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/logout"
    }
    res.unsafeRunSync
  }

  it should "initialize with B2C metadata using query string" in {
    val res = for {
      metadata <- OpenIDConnectConfiguration.getProviderMetadata[IO](
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/v2.0?p=b2c_1a_signup_signin"
      )
    } yield {
      metadata.issuer should startWith(
        "https://terradevb2c.b2clogin.com/"
      )
      metadata.authorizeEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/oauth2/v2.0/authorize?p=b2c_1a_signup_signin"
      metadata.tokenEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/oauth2/v2.0/token?p=b2c_1a_signup_signin"
      metadata.endSessionEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/oauth2/v2.0/logout?p=b2c_1a_signup_signin"
    }
    res.unsafeRunSync
  }

  "processQueryParams" should "inject the client_id to the scope" in {
    val interp = new OpenIDConnectInterpreter(ClientId("client_id"), "fake-authority", fakeMetadata, None, None, None)

    val params = List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile")
    val res = interp.processAuthorizeQueryParams(params)

    res shouldBe List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile client_id")
  }

  it should "inject the client_id and extra auth params" in {
    val interp = new OpenIDConnectInterpreter(ClientId("client_id"),
                                              "fake-authority",
                                              fakeMetadata,
                                              None,
                                              Some("extra=1&fields=more"),
                                              None
    )

    val params = List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile")
    val res = interp.processQueryParams(params)

    res shouldBe List("foo" -> "bar",
                      "abc" -> "123",
                      "scope" -> "openid email profile client_id",
                      "extra" -> "1",
                      "fields" -> "more"
    )
  }

  it should "not inject scope or extra auth params if not configured" in {
    val interp =
      new OpenIDConnectInterpreter(ClientId("client_id"),
                                   "https://accounts.google.com",
                                   googleMetadata,
                                   None,
                                   None,
                                   None
      )

    val params = List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile")
    val res = interp.processQueryParams(params)

    res shouldBe params
  }

  "processTokenFormFields" should "inject the client secret" in {
    val interp =
      new OpenIDConnectInterpreter(ClientId("client_id"),
                                   "https://accounts.google.com",
                                   googleMetadata,
                                   Some(ClientSecret("client_secret")),
                                   None,
                                   None
      )
    val fields = List(
      "client_id" -> "client_id",
      "access_token" -> "the-token"
    )
    val res = interp.processTokenFormFields(fields)
    res shouldBe (fields :+ ("client_secret" -> "client_secret"))
  }

  it should "not inject the client secret if absent" in {
    val interp =
      new OpenIDConnectInterpreter(
        ClientId("client_id"),
        "https://accounts.google.com",
        googleMetadata,
        None,
        None,
        None
      )
    val fields = List(
      "client_id" -> "client_id",
      "access_token" -> "the-token"
    )
    val res = interp.processTokenFormFields(fields)
    res shouldBe fields
  }

  it should "not inject the client secret if non-Google" in {
    val interp =
      new OpenIDConnectInterpreter(ClientId("client_id"),
                                   "fake-authority",
                                   fakeMetadata,
                                   Some(ClientSecret("client_secret")),
                                   None,
                                   None
      )
    val fields = List(
      "client_id" -> "client_id",
      "access_token" -> "the-token"
    )
    val res = interp.processTokenFormFields(fields)
    res shouldBe fields
  }

  "processSwaggerUiIndex" should "replace client ids and uri" in {
    val interp =
      new OpenIDConnectInterpreter(ClientId("client_id"),
                                   "fake-authority",
                                   fakeMetadata,
                                   None,
                                   None,
                                   Some(ClientId("extra_client_id"))
      )
    val source = Source.fromResource("swagger/index.html")
    val contents =
      try source.mkString
      finally source.close()
    val res = interp.processSwaggerUiIndex(contents, "/api-docs.yaml")
    res should include(
      """  var clientIds = {
        |    googleoauth: 'extra_client_id',
        |    oidc: 'client_id'
        |  }""".stripMargin
    )
    res should include("url: '/api-docs.yaml'")
  }
}
