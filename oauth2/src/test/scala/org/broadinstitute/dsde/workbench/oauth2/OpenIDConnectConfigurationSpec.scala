package org.broadinstitute.dsde.workbench.oauth2

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, FormData, StatusCodes, Uri}
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class OpenIDConnectConfigurationSpec
    extends AnyFlatSpecLike
    with Matchers
    with WorkbenchTestSuite
    with ScalatestRouteTest {

  "OpenIDConnectConfiguration" should "initialize with Google metadata" in {
    val res = for {
      metadata <- OpenIDConnectConfiguration.getProviderMetadata[IO]("https://accounts.google.com")
    } yield {
      metadata.authorizeEndpoint shouldBe "https://accounts.google.com/o/oauth2/v2/auth"
      metadata.tokenEndpoint shouldBe "https://oauth2.googleapis.com/token"
    }
    res.unsafeRunSync
  }

  it should "initialize with B2C metadata" in {
    val res = for {
      metadata <- OpenIDConnectConfiguration.getProviderMetadata[IO](
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin"
      )
    } yield {
      metadata.authorizeEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/authorize"
      metadata.tokenEndpoint shouldBe "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/token"
    }
    res.unsafeRunSync
  }

  "processAuthorizeQueryParams" should "inject the clientId" in {
    val interp =
      new OpenIDConnectInterpreter(OpenIDProviderMetadata("authorize", "token"), Some("client_id"), None, None)

    val params = List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile")
    val res = interp.processAuthorizeQueryParams(params)

    res shouldBe List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile client_id")
  }

  it should "inject the clientId and extra auth params" in {
    val interp = new OpenIDConnectInterpreter(OpenIDProviderMetadata("authorize", "token"),
                                              Some("client_id"),
                                              None,
                                              Some("extra=1&fields=more")
    )

    val params = List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile")
    val res = interp.processAuthorizeQueryParams(params)

    res shouldBe List("foo" -> "bar",
                      "abc" -> "123",
                      "scope" -> "openid email profile client_id",
                      "extra" -> "1",
                      "fields" -> "more"
    )
  }

  it should "not inject anything if unspecified" in {
    val interp =
      new OpenIDConnectInterpreter(OpenIDProviderMetadata("authorize", "token"), None, None, None)

    val params = List("foo" -> "bar", "abc" -> "123", "scope" -> "openid email profile")
    val res = interp.processAuthorizeQueryParams(params)

    res shouldBe params
  }

  "processTokenFormFields" should "inject the client secret" in {
    val interp =
      new OpenIDConnectInterpreter(OpenIDProviderMetadata("authorize", "token"), None, Some("client_secret"), None)
    val fields = List(
      "client_id" -> "client_id",
      "access_token" -> "the-token"
    )
    val res = interp.processTokenFormFields(fields)
    res shouldBe (fields :+ ("client_secret" -> "client_secret"))
  }

  it should "not inject the client secret if absent" in {
    val interp =
      new OpenIDConnectInterpreter(OpenIDProviderMetadata("authorize", "token"), None, None, None)
    val fields = List(
      "client_id" -> "client_id",
      "access_token" -> "the-token"
    )
    val res = interp.processTokenFormFields(fields)
    res shouldBe fields
  }

  "the akka-http authorize endpoint" should "redirect" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com")
      req = Get(Uri("/oauth2/authorize").withQuery(Query("""id=client_idwith"fun'characters&scope=foo+bar""")))
      _ <- req ~> config.toAkkaHttpRoute ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.Found
        header[Location].map(_.value) shouldBe Some(
          "https://accounts.google.com/o/oauth2/v2/auth?id=client_idwith%22fun'characters&scope=foo+bar"
        )
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "redirect with the clientId injected" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO](
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin",
        clientId = Some("some_client")
      )
      req = Get(Uri("/oauth2/authorize").withQuery(Query("""id=client_id&scope=foo+bar""")))
      _ <- req ~> config.toAkkaHttpRoute ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.Found
        header[Location].map(_.value) shouldBe Some(
          "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/authorize?id=client_id&scope=foo+bar+some_client"
        )
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "redirect with the clientId injected and extra parameters" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO](
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin",
        clientId = Some("some_client"),
        extraAuthParams = Some("foo=bar&abc=def")
      )
      req = Get(Uri("/oauth2/authorize").withQuery(Query("""id=client_id&scope=foo+bar""")))
      _ <- req ~> config.toAkkaHttpRoute ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.Found
        header[Location].map(_.value) shouldBe Some(
          "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin/oauth2/authorize?id=client_id&scope=foo+bar+some_client&foo=bar&abc=def"
        )
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "reject non-GET requests" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com")
      _ <- allMethods.filterNot(_ == GET).traverse { method =>
        new RequestBuilder(method)("/oauth2/authorize?id=client_id") ~> config.toAkkaHttpRoute ~> checkIO {
          handled shouldBe false
          rejection shouldBe a[MethodRejection]
        }
      }
    } yield ()
    res.unsafeRunSync()
  }

  "the akka-http token endpoint" should "proxy requests" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com")
      req = Post("/oauth2/token").withEntity(
        FormData("grant_type" -> "authorization_code", "code" -> "1234", "client_id" -> "some_client").toEntity
      )
      _ <- req ~> config.toAkkaHttpRoute ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.Unauthorized
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "reject non-POST requests" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com")
      _ <- allMethods.filterNot(_ == POST).traverse { method =>
        new RequestBuilder(method)("/oauth2/token") ~> config.toAkkaHttpRoute ~> checkIO {
          handled shouldBe false
          rejection shouldBe a[MethodRejection]
        }
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "reject requests without application/x-www-form-urlencoded content type" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com")
      req = Post("/oauth2/token").withEntity(ContentTypes.`application/json`, """{"some":"json"}""")
      _ <- req ~> config.toAkkaHttpRoute ~> checkIO {
        handled shouldBe false
        rejection shouldBe a[UnsupportedRequestContentTypeRejection]
      }
    } yield ()
    res.unsafeRunSync()
  }

  private def checkIO[T](body: => T): RouteTestResult => IO[T] = check(body).andThen(IO(_))

  private def allMethods = List(CONNECT, DELETE, GET, HEAD, PATCH, POST, PUT, TRACE)
}
