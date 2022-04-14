package org.broadinstitute.dsde.workbench.oauth2

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class OpenIDConnectInterpreter private[oauth2] (providerMetadata: OpenIDProviderMetadata,
                                                clientId: String,
                                                clientSecret: Option[String],
                                                extraAuthParams: Map[String, String],
                                                addClientIdToScope: Boolean
) extends OpenIDConnectConfiguration {
  private val scopeParam = "scope"
  private val clientSecretParam = "client_secret"

  override def toAkkaHttpRoute(implicit system: ActorSystem): Route =
    pathPrefix("oauth2") {
      path("authorize") {
        get {
          parameterSeq { params =>
            val newParams = processAuthorizeQueryParams(params)
            val newUri =
              Uri(providerMetadata.authorizeEndpoint).withQuery(Uri.Query(newParams: _*))
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
                  entity = FormData(addClientSecret(fields): _*).toEntity
                )
                Http().singleRequest(newRequest)
              }
            }
          }
        }
    }

  private def processAuthorizeQueryParams(params: Seq[(String, String)]): Seq[(String, String)] = {
    val paramsWithScope = if (addClientIdToScope) params.map { case (k, v) =>
      if (k == scopeParam) (k, v + " " + clientId) else (k, v)
    }
    else params

    paramsWithScope ++ extraAuthParams
  }

  private[oauth2] def addClientSecret(fields: Seq[(String, String)]): Seq[(String, String)] =
    clientSecret match {
      case Some(secret) =>
        if (!fields.exists(_._1 == clientSecretParam)) fields :+ (clientSecretParam -> secret)
        else fields
      case None => fields
    }
}
