package org.broadinstitute.dsde.workbench.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import java.nio.file.Paths

class OpenIDConnectAkkaHttpOps(private val config: OpenIDConnectConfiguration) {
  private val swaggerUiPath = "META-INF/resources/webjars/swagger-ui/4.10.3"

  def oauth2Routes(implicit actorSystem: ActorSystem): Route =
    pathPrefix("oauth2") {
      path("authorize") {
        get {
          parameterSeq { params =>
            val newQuery = Uri.Query(config.processAuthorizeQueryParams(params): _*)
            val newUri = Uri(config.getAuthorizationEndpoint).withQuery(newQuery)
            actorSystem.log.info("XXX newUri: " + newUri)
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
                  uri = Uri(config.getTokenEndpoint),
                  entity = FormData(config.processTokenFormFields(fields): _*).toEntity
                )
                Http().singleRequest(newRequest)
              }
            }
          }
        }
    }

  def swaggerRoutes(openApiYamlResource: String): Route = {
    val openApiFilename = Paths.get(openApiYamlResource).getFileName.toString
    path("") {
      get {
        complete(
          HttpEntity(ContentTypes.`application/octet-stream`, config.getSwaggerUiIndex("/" + openApiFilename).getBytes)
        )
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
