package org.broadinstitute.dsde.workbench.config

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream._
import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.{AuthToken, UserAuthToken}
import spray.json._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}


case class Credentials (email: String, password: String) extends SprayJsonSupport with DefaultJsonProtocol with LazyLogging {

  implicit val system: ActorSystem = ActorSystem()
  implicit val executor: ExecutionContextExecutor = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  implicit val tokenInfoJsonFormat = jsonFormat4(TokenInfo)

  def makeAuthToken(): AuthToken = {
    val auth: AuthToken = Credentials.cache.get(this)
    validateAuthToken(auth)
  }

  /**
    * Validate user's access token.
    *
    * https://developers.google.com/identity/protocols/OAuth2UserAgent#validate-access-token
    *
    * @param authToken
    * @return
    */
  private def validateAuthToken(authToken: AuthToken): AuthToken = {

    val validate_url = s"https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=${authToken}"

    logger.info(s"Get tokeninfo to validate AuthToken: ${authToken}, ${authToken.value}")

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(HttpMethods.GET, uri = validate_url))
    val response: HttpResponse = Await.result(responseFuture, 1.minutes)

    response.status match {
      case StatusCodes.OK =>
        val tokenInfo: Future[TokenInfo] = Unmarshal(response.entity).to[TokenInfo]
        val result = Await.result(tokenInfo, 5.seconds)
        if (result.expires_in.toInt < 300) {  // refresh token if expires_in < 5 min
          invalidateAndGetNewToken(this)
        } else {
          authToken
        }
      case _ =>
        logger.warn(s"Encountered error when getting tokeninfo for AuthToken: ${authToken}, ${authToken.value}. StatusCode: ${response.status} Response: $response")
        invalidateAndGetNewToken(this)
    }
  }

  private def invalidateAndGetNewToken(credential: Credentials): AuthToken = {
    Credentials.cache.invalidate(this)
    val newAuthToken: AuthToken = Credentials.cache.get(this)
    logger.info(s"User $email AuthToken refreshed. New AuthToken: ${newAuthToken} ${newAuthToken.value}")
    newAuthToken  // return new token
  }

  /*
  private def sendRequest(httpRequest: HttpRequest): TokenInfoResponse = {
    val responseFuture = Http().singleRequest(httpRequest)
    val TokenInfoResponseFuture: Future[TokenInfoResponse] = responseFuture.flatMap { response: HttpResponse =>
      val entity: ResponseEntity = response.entity
      Unmarshal(entity).to[TokenInfoResponse](mat, executor, mat)
    }
    val response: TokenInfoResponse = Await.result(TokenInfoResponseFuture, 5.minutes)
    response
  }
  */

  object Credentials {
    val cache = CacheBuilder.newBuilder()
      .expireAfterWrite(3600, TimeUnit.SECONDS)
      .build(
        new CacheLoader[Credentials, AuthToken] {
          def load(credentials: Credentials): AuthToken = {
            UserAuthToken(credentials)
          }
        }
      )
  }

}

case class TokenInfo(aud: String, user_id: String, scope: String, expires_in: String)

case class TokenInfoResponse(tokenInfo: Map[String, Option[TokenInfo]])



