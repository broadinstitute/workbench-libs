package org.broadinstitute.dsde.workbench.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{
  `Access-Control-Allow-Headers`,
  `Access-Control-Allow-Methods`,
  `Access-Control-Allow-Origin`
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Rejection, RejectionError, Route, ValidationRejection}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import io.circe.Encoder
import io.circe.syntax._
import org.broadinstitute.dsde.workbench.oauth2.OpenIDConnectAkkaHttpOps.ConfigurationResponse

import java.nio.file.Paths
import scala.concurrent.duration._

class OpenIDConnectAkkaHttpOps(private val config: OpenIDConnectConfiguration) {
  private val swaggerUiPath = "META-INF/resources/webjars/swagger-ui/5.17.14"
  private val policyParam = "p"

  def oauth2Routes(implicit actorSystem: ActorSystem): Route = {
    implicit val ec = actorSystem.dispatcher
    pathPrefix("oauth2") {
      path("authorize") {
        get {
          parameterSeq { params =>
            val authorizeUri = Uri(config.openIdProvider.metadata.authorizeEndpoint)
            val incomingQuery = config.processAuthorizeQueryParams(params)
            // Combine the query strings from the incoming request and the authorizeUri.
            // Parameters from the incoming request take precedence.
            val newQuery = Uri.Query((authorizeUri.query() ++ incomingQuery).toMap)
            val newUri = authorizeUri.withQuery(newQuery)
            redirect(newUri, StatusCodes.Found)
          }
        }
      } ~
        path("token") {
          post {
            formFieldSeq { fields =>
              parameter(policyParam.?) { policyInQuery =>
                val tokenUri = Uri(config.openIdProvider.metadata.tokenEndpoint)
                val policyInForm = fields.find(_._1 == policyParam).map(_._2)
                determinePolicyQuery(policyInQuery, tokenUri, policyInForm).fold(
                  reject(_),
                  query =>
                    complete {
                      val newRequest = HttpRequest(
                        POST,
                        uri = tokenUri.withQuery(query),
                        entity = FormData(fields: _*).toEntity
                      )
                      Http().singleRequest(newRequest).map(_.toStrict(5.seconds))
                    }
                )
              }
            }
          }
        } ~
        path("configuration") {
          get {
            complete {
              ConfigurationResponse(config.openIdProvider.authorityEndpoint, config.clientId)
            }
          }
        } ~
        path("logout") {
          get {
            parameterSeq { params =>
              val logoutUri = Uri(
                config.openIdProvider.metadata.endSessionEndpoint.getOrElse(
                  throw new Exception("Logout endpoint is only supported in Azure B2C.")
                )
              )
              // Combine the query strings from the incoming request and the uri.
              // Parameters from the incoming request take precedence.
              val newQuery = Uri.Query((logoutUri.query() ++ params).toMap)
              val newUri = logoutUri.withQuery(newQuery)

              redirect(newUri, StatusCodes.Found)
            }
          }
        }
    }
  }

  /**
   * Determine the query to use for the token request.
   * If the policy parameter is present in both the query and the form, and they are different, throw an exception.
   * If the policy parameter is present in the query or the form, use that value.
   * Otherwise, use the query from the tokenUri.
   */
  private def determinePolicyQuery(policyInQuery: Option[String],
                                   tokenUri: Uri,
                                   policyInForm: Option[String]
  ): Either[Rejection, Query] =
    (policyInQuery, policyInForm) match {
      case (Some(inQuery), Some(inForm)) if !inQuery.equalsIgnoreCase(inForm) =>
        Left(ValidationRejection(s"Policy parameter mismatch: $inQuery in query, $inForm in form."))
      case (Some(inQuery), _) => Right(Query(policyParam -> inQuery))
      case (_, Some(inForm))  => Right(Query(policyParam -> inForm))
      case _                  => Right(tokenUri.query())
    }

  def swaggerRoutes(openApiYamlResource: String): Route = {
    val openApiFilename = Paths.get(openApiYamlResource).getFileName.toString
    path("") {
      get {
        processSwaggerIndex(openApiFilename) {
          getFromResource("swagger/index.html")
        }
      }
    } ~
      path(openApiFilename) {
        get {
          processOpenApiYaml {
            // these headers allow the central swagger-ui to make requests to the OpenAPI yaml file
            respondWithHeaders(
              `Access-Control-Allow-Origin`.*,
              `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.OPTIONS),
              `Access-Control-Allow-Headers`("Content-Type", "api_key", "Authorization")
            ) {
              getFromResource(openApiYamlResource)
            }
          }
        }
      } ~
      // supporting files for swagger ui
      (pathPrefixTest("swagger-ui") | pathPrefixTest("oauth2-redirect") | pathPrefixTest("favicon")
        | pathSuffixTest("index.css") ) {
        get {
          getFromResourceDirectory(swaggerUiPath)
        }
      }
  }

  private def processOpenApiYaml =
    transformMaybeEmptyResponseEntity { original =>
      ByteString(config.processOpenApiYaml(original.utf8String))
    }

  private def processSwaggerIndex(openApiFilename: String): Directive0 =
    transformMaybeEmptyResponseEntity { original =>
      ByteString(config.processSwaggerUiIndex(original.utf8String, "/" + openApiFilename))
    }

  private def transformMaybeEmptyResponseEntity(transform: ByteString => ByteString): Directive0 =
    mapResponseEntity {
      // special case for empty response entities because transformDataBytes leads to an exception when the response
      // status code doesn't allow a body, e.g. 204 No Content or 304 Not Modified
      case empty @ Strict(_, content) if content.isEmpty => empty
      case entityFromJar =>
        entityFromJar.transformDataBytes(Flow.fromFunction(transform))
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
