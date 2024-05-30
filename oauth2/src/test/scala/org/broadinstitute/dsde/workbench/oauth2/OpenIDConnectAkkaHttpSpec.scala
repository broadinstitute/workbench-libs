package org.broadinstitute.dsde.workbench.oauth2

import akka.http.javadsl.server.ValidationRejection
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.{
  `Access-Control-Allow-Headers`,
  `Access-Control-Allow-Methods`,
  `Access-Control-Allow-Origin`,
  Location
}
import akka.http.scaladsl.model.{ContentTypes, FormData, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, UnsupportedRequestContentTypeRejection}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.syntax.all._
import io.circe.Decoder
import io.circe.parser._
import io.circe.syntax._
import org.broadinstitute.dsde.workbench.model.ErrorReportSource
import org.broadinstitute.dsde.workbench.oauth2.OpenIDConnectAkkaHttpOps.{ConfigurationResponse, _}
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._

class OpenIDConnectAkkaHttpSpec
    extends AnyFlatSpecLike
    with Matchers
    with WorkbenchTestSuite
    with ScalatestRouteTest
    with BeforeAndAfterAll {
  var mockServer: Http.ServerBinding = _

  override def beforeAll(): Unit = {
    val backendRoute = path(".well-known" / "openid-configuration") {
      get {
        parameterSeq { params =>
          complete {
            Map(
              "issuer" -> "test",
              "authorization_endpoint" -> Uri("http://localhost:9000/authorize")
                .withQuery(Query(params.toMap))
                .toString(),
              "token_endpoint" -> Uri("http://localhost:9000/token")
                .withQuery(Query(params.toMap))
                .toString()
            )
          }
        }
      }
    } ~
      path("token") {
        post {
          formFieldSeq { _ =>
            parameter("p".?) { policy =>
              complete(Map("token" -> s"a-token${policy.getOrElse("")}"))
            }
          }
        }
      }
    mockServer = Await.result(Http().newServerAt("0.0.0.0", 9000).bind(backendRoute), 10.seconds)
  }

  override def afterAll(): Unit =
    Await.result(mockServer.terminate(3.seconds), Duration.Inf)

  "authorize endpoint" should "redirect with extra parameters" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO](
        "http://localhost:9000?p=b2c_1a_signup_signin",
        ClientId("some_client"),
        extraAuthParams = Some("foo=bar&abc=def")
      )
      req = Get(Uri("/oauth2/authorize").withQuery(Query("""id=client_id&scope=foo+bar""")))
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.Found
        val uri = Uri(header[Location].getOrElse(fail("location header missing")).value)
        uri.query().toMap shouldBe Map(
          "id" -> "client_id",
          "scope" -> "foo bar some_client",
          "foo" -> "bar",
          "abc" -> "def",
          "p" -> "b2c_1a_signup_signin"
        )
        uri.path shouldBe Uri.Path("/authorize")
        uri.authority.toString() shouldBe "localhost:9000"
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "redirect to a different policy" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO](
        "http://localhost:9000?p=b2c_1a_signup_signin",
        ClientId("some_client")
      )
      req = Get(Uri("/oauth2/authorize").withQuery(Query("""p=some-other-policy""")))
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.Found
        header[Location].map(_.value) shouldBe Some(
          "http://localhost:9000/authorize?p=some-other-policy"
        )
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "reject non-GET requests" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000", ClientId("some_client"))
      _ <- allMethods.filterNot(_ == GET).traverse { method =>
        new RequestBuilder(method)("/oauth2/authorize?id=client_id") ~> config.oauth2Routes ~> checkIO {
          handled shouldBe false
          rejection shouldBe a[MethodRejection]
        }
      }
    } yield ()
    res.unsafeRunSync()
  }

  "the token endpoint" should "proxy requests" in {
    val formData = Map("grant_type" -> "authorization_code", "code" -> "1234", "client_id" -> "some_client")

    val res = for {
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000", ClientId("some_client"))
      req = Post("/oauth2/token").withEntity(
        FormData(formData).toEntity
      )
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        val resp = responseAs[String]
        resp shouldBe Map("token" -> "a-token").asJson.noSpaces
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "proxy requests with a policy field" in {
    val formData = Map("grant_type" -> "authorization_code",
                       "code" -> "1234",
                       "client_id" -> "some_client",
                       "p" -> "some-other-policy"
    )

    val res = for {
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000", ClientId("some_client"))
      req = Post("/oauth2/token").withEntity(
        FormData(formData).toEntity
      )
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        val resp = responseAs[String]
        // mock server appends the policy to the token
        resp shouldBe Map("token" -> "a-tokensome-other-policy").asJson.noSpaces
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "proxy requests with a policy query parameter" in {
    val formData = Map("grant_type" -> "authorization_code", "code" -> "1234", "client_id" -> "some_client")

    val res = for {
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000", ClientId("some_client"))
      req = Post("/oauth2/token?p=some-other-policy").withEntity(
        FormData(formData).toEntity
      )
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        val resp = responseAs[String]
        // mock server appends the policy to the token
        resp shouldBe Map("token" -> "a-tokensome-other-policy").asJson.noSpaces
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "proxy requests with same policy in form and query parameter" in {
    val formData = Map("grant_type" -> "authorization_code",
                       "code" -> "1234",
                       "client_id" -> "some_client",
                       "p" -> "Some-Other-Policy" // case is different to test case insensitivity
    )

    val res = for {
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000", ClientId("some_client"))
      req = Post("/oauth2/token?p=some-other-policy").withEntity(
        FormData(formData).toEntity
      )
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        val resp = responseAs[String]
        // mock server appends the policy to the token
        resp shouldBe Map("token" -> "a-tokensome-other-policy").asJson.noSpaces
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "reject requests with different policy in form and query parameter" in {
    val formData = Map("grant_type" -> "authorization_code",
                       "code" -> "1234",
                       "client_id" -> "some_client",
                       "p" -> "Some-Other-Policy" // case is different to test case insensitivity
    )

    val res = for {
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000", ClientId("some_client"))
      req = Post("/oauth2/token?p=some-other-other-policy").withEntity(
        FormData(formData).toEntity
      )
      _ <- req ~> config.oauth2Routes ~> checkIO {
        handled shouldBe false
        rejection shouldBe a[ValidationRejection]
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "reject non-POST requests" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000", ClientId("some_client"))
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
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000", ClientId("some_client"))
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
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000", ClientId("some_client"))
      req = Get("/")
      _ <- req ~> config.swaggerRoutes("swagger/swagger.yaml") ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/html(UTF-8)`
        val resp = responseAs[String]
        resp should include("clientId: 'some_client'")
        resp should include("url: '/swagger.yaml'")
      }
    } yield ()
    res.unsafeRunSync()
  }

  it should "return open api yaml" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO]("http://localhost:9000",
                                               ClientId("some_client"),
                                               authorityEndpointWithGoogleBillingScope =
                                                 Some("http://localhost:9000?scope=billing")
      )
      req = Get("/swagger.yaml")
      _ <- req ~> config.swaggerRoutes("swagger/swagger.yaml") ~> checkIO {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/octet-stream`
        val resp = responseAs[String]
        resp should include("Everything about your Pets")
        resp should include(
          "http://localhost:9000/.well-known/openid-configuration?scope=billing http://localhost:9000/.well-known/openid-configuration http://localhost:9000/authorize http://localhost:9000/token http://localhost:9000/authorize?scope=billing http://localhost:9000/token?scope=billing"
        )
        header[`Access-Control-Allow-Origin`].map(_.value) shouldBe Some("*")
        header[`Access-Control-Allow-Methods`] shouldBe Some(`Access-Control-Allow-Methods`(GET, OPTIONS))
        header[`Access-Control-Allow-Headers`] shouldBe Some(
          `Access-Control-Allow-Headers`("Content-Type", "api_key", "Authorization")
        )
      }
    } yield ()
    res.unsafeRunSync()
  }

  "the configuration route" should "return b2c config" in {
    val res = for {
      config <- OpenIDConnectConfiguration[IO](
        "http://localhost:9000",
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
          "http://localhost:9000"
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
