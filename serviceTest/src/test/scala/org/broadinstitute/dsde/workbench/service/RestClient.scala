package org.broadinstitute.dsde.workbench.service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{Multipart, _}
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.util.Retry

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

trait RestClient extends Retry with LazyLogging {

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val materializer: Materializer = Materializer(system)

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  implicit protected class JsonStringUtil(s: String) {
    def fromJsonMapAs[A](key: String): Option[A] = parseJsonAsMap.get(key)
    def parseJsonAsMap[A]: Map[String, A] = mapper.readValue(s, classOf[Map[String, A]])
  }

  private def makeAuthHeader(token: AuthToken): Authorization =
    headers.Authorization(OAuth2BearerToken(token.value))

  def encodeUri(path: String): String = {
    val pattern =
      """(https?)?:\/{2}+([\dA-z_.-]+)+(:[\d]+)??(\/[~0-9A-z\#\+\%@\.\/_ -]+)(\?[0-9A-z\+\%@\/&\[\];=_-]+)?""".r

    def toUri(url: String) = url match {
      case pattern(theScheme, theHost, thePort, thePath, theParams) =>
        val p: Int = Try(thePort.replace(":", "").toInt).toOption.getOrElse(0)
        val qp: Option[String] = Try(theParams.replace("?", "")).toOption
        Uri.from(scheme = theScheme, port = p, host = theHost, path = thePath, queryString = qp)
    }
    toUri(path).toString
  }

  // Send an http request with retries
  def sendRequest(httpRequest: HttpRequest): HttpResponse = {
    val responseFuture = retryExponentially() { () =>
      for {
        response <- Http().singleRequest(request = httpRequest)
        strictEntity <-
          if (response.entity.isStrict()) {
            // if the response is not strict then repeated reads of the entity with fail.
            // some of the test code does repeated reads so make it strict.
            Future.successful(response.entity)
          } else {
            response.entity.toStrict(5 minutes)
          }
      } yield {
        val strictResponse = response.withEntity(strictEntity)

        logRequestResponse(httpRequest, strictResponse)
        // retry any 401 or 500 errors - this is because we have seen the proxy get backend errors
        // from google querying for token info which causes a 401 if it is at the level if the
        // service being directly called or a 500 if it happens at a lower level service
        if (
          strictResponse.status == StatusCodes.Unauthorized || strictResponse.status == StatusCodes.InternalServerError
        ) {
          throwRestException(strictResponse)
        } else {
          strictResponse
        }
      }
    }
    Await.result(responseFuture, 5.minutes)
  }

  def entityToString(entity: HttpEntity): String =
    // try to represent the entity in a meaningful way - if we can easily read the data (i.e. it's Strict),
    // take the first 500 chars. Else, bail and use the default HttpRequest.Entity.toString, which isn't
    // all that helpful.
    entity match {
      case se: HttpEntity.Strict =>
        val rawEntityString = se.data.utf8String
        val shorterString = if (rawEntityString.length > 500) {
          s"${rawEntityString.take(500)}..."
        } else {
          rawEntityString
        }
        // attempt to mask out tokens
        shorterString.replaceAll("""ya29[\w\d.-]+""", "***REDACTED***")
      case x => x.toString
    }

  def logRequestResponse(httpRequest: HttpRequest, response: HttpResponse): Unit =
    logger.debug(
      s"API request: ${httpRequest.method.value} ${httpRequest.uri.toString()} " +
        s"with headers (${httpRequest.headers.map(_.name()).mkString(",")}) " +
        s"${entityToString(httpRequest.entity)}\n" +
        s"API response: ${response.status.value} ${entityToString(response.entity)}"
    )

  def extractResponseString(response: HttpResponse): String = {
    val responseStringFuture: Future[String] = response.entity.toStrict(5 minutes).map(_.data.utf8String)
    Await.result(responseStringFuture, 5 minutes)
  }

  def parseResponse(response: HttpResponse): String =
    response.status.isSuccess() match {
      case true =>
        extractResponseString(response)
      case _ =>
        logger.error(extractResponseString(response)) // write to test log
        throwRestException(response)
    }

  private def throwRestException(response: HttpResponse) =
    throw RestException(extractResponseString(response), response.status)

  import scala.reflect.{classTag, ClassTag}
  def parseResponseAs[T: ClassTag](response: HttpResponse): T = {
    // https://stackoverflow.com/questions/6200253/scala-classof-for-type-parameter
    val classT: Class[T] = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    mapper.readValue(parseResponse(response), classT)
  }

  // return Some(T) on success, None on failure
  def parseResponseOption[T: ClassTag](response: HttpResponse): Option[T] =
    if (response.status.isSuccess())
      Option(parseResponseAs[T](response))
    else
      None

  private def requestWithJsonContent(method: HttpMethod,
                                     uri: String,
                                     content: Any,
                                     httpHeaders: List[HttpHeader] = List.empty
  )(implicit token: AuthToken): String = {
    val req = HttpRequest(method,
                          encodeUri(uri),
                          List(makeAuthHeader(token)) ++ httpHeaders,
                          HttpEntity(ContentTypes.`application/json`, mapper.writeValueAsString(content))
    )
    parseResponse(sendRequest(req))
  }

  def postRequestWithMultipart(uri: String, name: String, content: String)(implicit token: AuthToken): String = {
    val part = Multipart.FormData.BodyPart.Strict(name, HttpEntity(ByteString(content)))
    val formData = Multipart.FormData(Source.single(part))
    val req = HttpRequest(POST, encodeUri(uri), List(makeAuthHeader(token)), formData.toEntity())
    parseResponse(sendRequest(req))
  }

  private def requestBasic(method: HttpMethod, uri: String, httpHeaders: List[HttpHeader] = List())(implicit
    token: AuthToken
  ): HttpResponse = {
    val req = HttpRequest(method, encodeUri(uri), List(makeAuthHeader(token)) ++ httpHeaders)
    sendRequest(req)
  }

  def patchRequest(uri: String, content: Any, httpHeaders: List[HttpHeader] = List())(implicit
    token: AuthToken
  ): String =
    requestWithJsonContent(PATCH, uri, content, httpHeaders)

  def postRequest(uri: String, content: Any = None, httpHeaders: List[HttpHeader] = List())(implicit
    token: AuthToken
  ): String =
    requestWithJsonContent(POST, uri, content, httpHeaders)

  def putRequest(uri: String, content: Any = None, httpHeaders: List[HttpHeader] = List())(implicit
    token: AuthToken
  ): String =
    requestWithJsonContent(PUT, uri, content, httpHeaders)

  def deleteRequest(uri: String, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String =
    requestWithJsonContent(DELETE, uri, None, httpHeaders)

  def deleteRequestWithContent(uri: String, content: Any = None, httpHeaders: List[HttpHeader] = List())(implicit
    token: AuthToken
  ): String =
    requestWithJsonContent(DELETE, uri, content, httpHeaders)

  def getRequest(uri: String, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): HttpResponse =
    requestBasic(GET, uri, httpHeaders)
}
