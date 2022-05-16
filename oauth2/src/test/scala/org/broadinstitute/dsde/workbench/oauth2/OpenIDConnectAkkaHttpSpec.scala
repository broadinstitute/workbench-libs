package org.broadinstitute.dsde.workbench.oauth2

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, FormData, StatusCodes, Uri}
import akka.http.scaladsl.server.{MethodRejection, UnsupportedRequestContentTypeRejection}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import io.circe.Decoder
import io.circe.parser._
import org.broadinstitute.dsde.workbench.oauth2.OpenIDConnectAkkaHttpOps.ConfigurationResponse
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class OpenIDConnectAkkaHttpSpec extends AnyFlatSpecLike with Matchers with WorkbenchTestSuite with ScalatestRouteTest {
  "authorize endpoint" should "redirect" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com", ClientId("client_id"))
      req = Get(Uri("/oauth2/authorize").withQuery(Query("""id=client_idwith"fun'characters&scope=foo+bar""")))
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.Found
        header[Location].map(_.value) shouldBe Some(
          "https://accounts.google.com/o/oauth2/v2/auth?id=client_idwith%22fun'characters&scope=foo+bar"
        )
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "redirect with extra parameters" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO](
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin",
        ClientId("some_client"),
        extraAuthParams = Some("foo=bar&abc=def")
      )
      req = Get(Uri("/oauth2/authorize").withQuery(Query("""id=client_id&scope=foo+bar""")))
      _ <- req ~> config.oauth2Routes ~> checkIO {
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
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com", ClientId("some_client"))
      _ <- allMethods.filterNot(_ == GET).traverse { method =>
        new RequestBuilder(method)("/oauth2/authorize?id=client_id") ~> config.oauth2Routes ~> checkIO {
          handled shouldBe false
          rejection shouldBe a[MethodRejection]
        }
      }
    } yield ()
    res.unsafeRunSync()
  }

  // Ignored because of issues calling the third party endpoint from github actions
  "the token endpoint" should "proxy requests" ignore {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com", ClientId("some_client"))
      req = Post("/oauth2/token").withEntity(
        FormData("grant_type" -> "authorization_code", "code" -> "1234", "client_id" -> "some_client").toEntity
      )
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.Unauthorized
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "reject non-POST requests" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com", ClientId("some_client"))
      _ <- allMethods.filterNot(_ == POST).traverse { method =>
        new RequestBuilder(method)("/oauth2/token") ~> config.oauth2Routes ~> checkIO {
          handled shouldBe false
          rejection shouldBe a[MethodRejection]
        }
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "reject requests without application/x-www-form-urlencoded content type" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com", ClientId("some_client"))
      req = Post("/oauth2/token").withEntity(ContentTypes.`application/json`, """{"some":"json"}""")
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe false
        rejection shouldBe a[UnsupportedRequestContentTypeRejection]
      }
    } yield ()
    res.unsafeRunSync()
  }

  "the swagger routes" should "return index.html" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com",
                                               ClientId("some_client"),
                                               extraGoogleClientId = Some(ClientId("extra_client"))
      )
      req = Get("/")
      _ <- req ~> config.swaggerRoutes("swagger/swagger.yaml") ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/html(UTF-8)`
        val resp = responseAs[String]
        resp should include(
          """  var clientIds = {
            |    googleoauth: 'extra_client',
            |    oidc: 'some_client'
            |  }""".stripMargin
        )
        resp should include("url: '/swagger.yaml'")
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "return swagger yaml" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("https://accounts.google.com",
                                               ClientId("some_client"),
                                               extraGoogleClientId = Some(ClientId("extra_client"))
      )
      req = Get("/swagger.yaml")
      _ <- req ~> config.swaggerRoutes("swagger/swagger.yaml") ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/octet-stream`
        val resp = responseAs[String]
        resp should include("Everything about your Pets")
      }
    } yield ()
    res.unsafeRunSync()
  }

  "the configuration route" should "return google config" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO](
        "https://accounts.google.com",
        ClientId("some_client")
      )
      req = Get(Uri("/oauth2/configuration"))
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        val responseString = responseAs[String]
        val configResponse = decode[ConfigurationResponse](responseString)
        configResponse.map(_.clientId) shouldBe Right(ClientId("some_client"))
        configResponse.map(_.authorityEndpoint) shouldBe Right(
          "https://accounts.google.com"
        )
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "return b2c config" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO](
        "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin",
        ClientId("some_client")
      )
      req = Get(Uri("/oauth2/configuration"))
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        val responseString = responseAs[String]
        val configResponse = decode[ConfigurationResponse](responseString)
        configResponse.map(_.clientId) shouldBe Right(ClientId("some_client"))
        configResponse.map(_.authorityEndpoint) shouldBe Right(
          "https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/b2c_1a_signup_signin"
        )
      }
    } yield ()
    res.unsafeRunSync()
  }

  private def checkIO[T](body: => T): RouteTestResult => IO[T] = check(body).andThen(IO(_))

  private def allMethods = List(CONNECT, DELETE, GET, HEAD, PATCH, POST, PUT, TRACE)

  implicit private val clientIdDecoder: Decoder[ClientId] = Decoder.decodeString.map(ClientId)
  implicit private val configurationResponseDecoder: Decoder[ConfigurationResponse] =
    Decoder.forProduct2("authorityEndpoint", "clientId")(ConfigurationResponse.apply)
}
