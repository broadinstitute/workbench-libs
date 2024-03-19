package org.broadinstitute.dsde.workbench.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import io.circe.Encoder
import io.circe.syntax._
import org.broadinstitute.dsde.workbench.oauth2.OpenIDConnectAkkaHttpOps.ConfigurationResponse

import java.nio.file.Paths
import scala.concurrent.duration._

class OpenIDConnectAkkaHttpOps(private val config: OpenIDConnectConfiguration) {
  private val swaggerUiPath = "META-INF/resources/webjars/swagger-ui/4.11.1"
  private val policyParam = "p"

  def oauth2Routes(implicit actorSystem: ActorSystem): Route = {
    implicit val ec = actorSystem.dispatcher
    pathPrefix("oauth2") {
      path("authorize") {
        get {
          parameterSeq { params =>
            val authorizeUri = Uri(config.providerMetadata.authorizeEndpoint)
            val newUri = applyQueryParams(authorizeUri, params)

            redirect(newUri, StatusCodes.Found)
          }
        }
      } ~
        path("token") {
          post {
            formFieldSeq { fields =>
              complete {
                val tokenUri = Uri(config.providerMetadata.tokenEndpoint)
                // If the policy was passed as a parameter in the incoming request,
                // pass it to the token endpoint as a query string parameter.
                val newRequest = HttpRequest(
                  POST,
                  uri = tokenUri
                    .withQuery(fields.find(_._1 == policyParam).map(Query(_)).getOrElse(tokenUri.query())),
                  entity = FormData(config.processTokenFormFields(fields): _*).toEntity
                )
                Http().singleRequest(newRequest).map(_.toStrict(5.seconds))
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
        } ~
        path("logout") {
          get {
            parameterSeq { params =>
              val logoutUri = Uri(config.providerMetadata.endSessionEndpoint.getOrElse(
                throw new Exception("Logout endpoint is only supported in Azure B2C.")))
              val newUri = applyQueryParams(logoutUri, params)
              redirect(newUri, StatusCodes.Found)
            }
          }
        }
    }
  }

  private def applyQueryParams(uri: Uri, params: Seq[(String, String)]): Uri = {
    val incomingQuery = config.processQueryParams(params)
    // Combine the query strings from the incoming request and the uri.
    // Parameters from the incoming request take precedence.
    val newQuery = Uri.Query((uri.query() ++ incomingQuery).toMap)
    uri.withQuery(newQuery)
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
