package org.broadinstitute.dsde.workbench.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class OpenIDConnectInterpreter private[oauth2] (providerMetadata: OpenIDProviderMetadata,
                                                clientId: Option[String],
                                                clientSecret: Option[String],
                                                extraAuthParams: Option[String]
) extends OpenIDConnectConfiguration {
  private val scopeParam = "scope"
  private val clientSecretParam = "client_secret"

  override def getAuthorizationEndpoint: String = providerMetadata.authorizeEndpoint

  override def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)] = {
    val paramsWithScope = clientId match {
      case Some(clientId) =>
        params.map { case (k, v) =>
          if (k == scopeParam) (k, v + " " + clientId) else (k, v)
        }
      case None => params
    }

    val paramsWithScopeAndExtraAuthParams =
      paramsWithScope ++ extraAuthParams.map(eap => Uri.Query(eap).toSeq).getOrElse(Uri.Query.Empty)

    paramsWithScopeAndExtraAuthParams
  }

  override def getTokenEndpoint: String = providerMetadata.tokenEndpoint

  override def processTokenFormFields(fields: Seq[(String, String)]): Seq[(String, String)] =
    clientSecret match {
      case Some(secret) =>
        if (!fields.exists(_._1 == clientSecretParam)) fields :+ (clientSecretParam -> secret)
        else fields
      case None => fields
    }

  override def toAkkaHttpRoute(implicit system: ActorSystem): Route =
    pathPrefix("oauth2") {
      path("authorize") {
        get {
          parameterSeq { params =>
            val newQuery = Uri.Query(processAuthorizeQueryParams(params): _*)
            val newUri = Uri(providerMetadata.authorizeEndpoint).withQuery(newQuery)
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
                  uri = Uri(providerMetadata.tokenEndpoint),
                  entity = FormData(processTokenFormFields(fields): _*).toEntity
                )
                Http().singleRequest(newRequest)
              }
            }
          }
        }
    }
}
