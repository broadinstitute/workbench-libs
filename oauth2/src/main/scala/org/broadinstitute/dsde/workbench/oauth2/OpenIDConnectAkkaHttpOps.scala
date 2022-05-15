package org.broadinstitute.dsde.workbench.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import io.circe.Encoder
import io.circe.syntax._
import org.broadinstitute.dsde.workbench.oauth2.OpenIDConnectAkkaHttpOps.ConfigurationResponse

import java.nio.file.Paths

class OpenIDConnectAkkaHttpOps(private val config: OpenIDConnectConfiguration) {
  private val swaggerUiPath = "META-INF/resources/webjars/swagger-ui/4.10.3"

  def oauth2Routes(implicit actorSystem: ActorSystem): Route =
    pathPrefix("oauth2") {
      path("authorize") {
        get {
          parameterSeq { params =>
            val newQuery = Uri.Query(config.processAuthorizeQueryParams(params): _*)
            val newUri = Uri(config.providerMetadata.authorizeEndpoint).withQuery(newQuery)
            redirect(newUri, StatusCodes.Found)
          }
        }
      } ~
        path("token") {
          post {
            formFieldSeq { fields =>
              complete {
                val newRequest = HttpRequest(
                  POST,
                  uri = Uri(config.providerMetadata.tokenEndpoint),
                  entity = FormData(config.processTokenFormFields(fields): _*).toEntity
                )
                Http().singleRequest(newRequest)
              }
            }
          }
        } ~
        path("configuration") {
          get {
            complete {
              ConfigurationResponse(config.authorityEndpoint, config.clientId)
            }
          }
        }
    }

  def swaggerRoutes(openApiYamlResource: String): Route = {
    val openApiFilename = Paths.get(openApiYamlResource).getFileName.toString
    path("") {
      get {
        mapResponseEntity { entityFromJar =>
          entityFromJar.transformDataBytes(Flow.fromFunction { original =>
            ByteString(config.processSwaggerUiIndex(original.utf8String, "/" + openApiFilename))
          })
        } {
          getFromResource("swagger/index.html")
        }
      }
    } ~
      path(openApiFilename) {
        get {
          getFromResource(openApiYamlResource)
        }
      } ~
      (pathPrefixTest("swagger-ui") | pathPrefixTest("oauth2-redirect") | pathSuffixTest("js")
        | pathSuffixTest("css") | pathPrefixTest("favicon")) {
        get {
          getFromResourceDirectory(swaggerUiPath)
        }
      }
  }
}

object OpenIDConnectAkkaHttpOps {
  case class ConfigurationResponse(authorityEndpoint: String, clientId: ClientId)

  implicit final def marshaller[A: Encoder]: ToEntityMarshaller[A] =
    Marshaller.withFixedContentType(`application/json`) { a =>
      HttpEntity(`application/json`, a.asJson.noSpaces)
    }

  implicit val clientIdEncoder: Encoder[ClientId] =
    Encoder.encodeString.contramap(_.value)
  implicit val configurationResponseEncoder: Encoder[ConfigurationResponse] =
    Encoder.forProduct2("authorityEndpoint", "clientId")(x => (x.authorityEndpoint, x.clientId))
}
