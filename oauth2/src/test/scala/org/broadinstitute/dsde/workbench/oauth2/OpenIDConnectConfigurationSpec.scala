package org.broadinstitute.dsde.workbench.oauth2

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, FormData, StatusCodes, Uri}
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class OpenIDConnectConfigurationSpec
    extends AnyFlatSpecLike
    with Matchers
    with WorkbenchTestSuite
    with ScalatestRouteTest {

  "OpenIDConnectConfiguration" should "initialize with Google metadata" in {
    OpenIDConnectConfiguration
      .getProviderMetadata[IO]("https://accounts.google.com")
      .use { metadata =>
        IO {
          metadata.authorizeEndpoint shouldBe "https://accounts.google.com/o/oauth2/v2/auth"
          metadata.tokenEndpoint shouldBe "https://oauth2.googleapis.com/token"
        }
      }
      .unsafeRunSync
  }

  it should "initialize with B2C metadata" in {
    OpenIDConnectConfiguration
      .getProviderMetadata[IO]("https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin")
      .use { metadata =>
        IO {
          metadata.authorizeEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/authorize"
          metadata.tokenEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/token"
        }
      }
      .unsafeRunSync
  }

  "OpenIDConnectInterpreter" should "inject the client secret" in {
    val interp = new OpenIDConnectInterpreter[IO](OpenIDProviderMetadata("authorize", "token"),
                                                  "client_id",
                                                  Some("client_secret"),
                                                  Map.empty,
                                                  true
    )
    val fields = List(
      "client_id" -> "client_id",
      "access_token" -> "the-token"
    )
    val res = interp.addClientSecret(fields)
    res shouldBe (fields :+ ("client_secret", "client_secret"))
  }

  it should "not inject the client secret if absent" in {
    val interp =
      new OpenIDConnectInterpreter[IO](OpenIDProviderMetadata("authorize", "token"), "client_id", None, Map.empty, true)
    val fields = List(
      "client_id" -> "client_id",
      "access_token" -> "the-token"
    )
    val res = interp.addClientSecret(fields)
    res shouldBe fields
  }

  "the akka-http authorize endpoint" should "redirect" in {
    OpenIDConnectConfiguration
      .resource[IO]("https://accounts.google.com", "some_client")
      .use { oidc =>
        Get(
          Uri("/oauth2/authorize").withQuery(Query("""id=client_idwith"fun'characters&scope=foo+bar"""))
        ) ~> oidc.toAkkaHttpRoute ~> checkIO {
          handled shouldBe true
          status shouldBe StatusCodes.Found
          header[Location].map(_.value) shouldBe Some(
            "https://accounts.google.com/o/oauth2/v2/auth?id=client_idwith%22fun'characters&scope=foo+bar"
          )
        }
      }
      .unsafeRunSync()
  }

  it should "redirect with the clientId injected" in {
    OpenIDConnectConfiguration
      .resource[IO]("https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin",
                    "some_client",
                    addClientIdToScope = true
      )
      .use { oidc =>
        Get(
          Uri("/oauth2/authorize").withQuery(Query("""id=client_id&scope=foo+bar"""))
        ) ~> oidc.toAkkaHttpRoute ~> checkIO {
          handled shouldBe true
          status shouldBe StatusCodes.Found
          header[Location].map(_.value) shouldBe Some(
            "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/authorize?id=client_id&scope=foo+bar+some_client"
          )
        }
      }
      .unsafeRunSync()
  }

  it should "redirect with the clientId injected and extra parameters" in {
    OpenIDConnectConfiguration
      .resource[IO](
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin",
        "some_client",
        addClientIdToScope = true,
        extraAuthParams = Map("foo" -> "bar", "abc" -> "def")
      )
      .use { oidc =>
        Get(
          Uri("/oauth2/authorize").withQuery(Query("""id=client_id&scope=foo+bar"""))
        ) ~> oidc.toAkkaHttpRoute ~> checkIO {
          handled shouldBe true
          status shouldBe StatusCodes.Found
          header[Location].map(_.value) shouldBe Some(
            "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/authorize?id=client_id&scope=foo+bar+some_client&foo=bar&abc=def"
          )
        }
      }
      .unsafeRunSync()
  }

  it should "reject non-GET requests" in {
    OpenIDConnectConfiguration
      .resource[IO]("https://accounts.google.com", "some_client")
      .use { oidc =>
        allMethods.filterNot(_ == GET).traverse { method =>
          new RequestBuilder(method)("/oauth2/authorize?id=client_id") ~> oidc.toAkkaHttpRoute ~> checkIO {
            handled shouldBe false
            rejection shouldBe a[MethodRejection]
          }
        }
      }
      .unsafeRunSync()
  }

  "the akka-http token endpoint" should "proxy requests" in {
    OpenIDConnectConfiguration
      .resource[IO]("https://accounts.google.com", "some_client")
      .use { oidc =>
        Post("/oauth2/token")
          .withEntity(
            FormData("grant_type" -> "authorization_code", "code" -> "1234", "client_id" -> "some_client").toEntity
          ) ~> oidc.toAkkaHttpRoute ~> checkIO {
          handled shouldBe true
          status shouldBe StatusCodes.Unauthorized
        }
      }
      .unsafeRunSync()
  }

  it should "reject non-POST requests" in OpenIDConnectConfiguration
    .resource[IO]("https://accounts.google.com", "some_client")
    .use { oidc =>
      allMethods.filterNot(_ == POST).traverse { method =>
        new RequestBuilder(method)("/oauth2/token") ~> oidc.toAkkaHttpRoute ~> checkIO {
          handled shouldBe false
          rejection shouldBe a[MethodRejection]
        }
      }
    }
    .unsafeRunSync()

  it should "reject requests without application/x-www-form-urlencoded content type" in OpenIDConnectConfiguration
    .resource[IO]("https://accounts.google.com", "some_client")
    .use { oidc =>
      Post("/oauth2/token").withEntity(ContentTypes.`application/json`,
                                       """{"some":"json"}"""
      ) ~> oidc.toAkkaHttpRoute ~> checkIO {
        handled shouldBe false
        rejection shouldBe a[UnsupportedRequestContentTypeRejection]
      }
    }
    .unsafeRunSync()

  private def checkIO[T](body: => T): RouteTestResult => IO[T] = check(body).andThen(IO(_))

  private def allMethods = List(CONNECT, DELETE, GET, HEAD, PATCH, POST, PUT, TRACE)
}
