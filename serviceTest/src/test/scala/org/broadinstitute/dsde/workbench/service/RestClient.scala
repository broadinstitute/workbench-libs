package org.broadinstitute.dsde.workbench.service

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{Multipart, _}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.util.Retry

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.Try


trait RestClient extends Retry with LazyLogging {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  //implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("blocking-dispatcher")

  // increase TCP idle-timeout
  // val idleTimeout = 2.minutes
  // val connectionSettings = ClientConnectionSettings(system).withIdleTimeout(idleTimeout)
  // val connectionPoolSettings = ConnectionPoolSettings(system).withConnectionSettings(connectionSettings)
  // val connectionSettings: ClientConnectionSettings = ClientConnectionSettings(system).withIdleTimeout(idleTimeout)
  // val connectionPoolSettings = ConnectionPoolSettings(system).withConnectionSettings(connectionSettings)

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  implicit protected class JsonStringUtil(s: String) {
    def fromJsonMapAs[A](key: String): Option[A] = parseJsonAsMap.get(key)
    def parseJsonAsMap[A]: Map[String, A] = mapper.readValue(s, classOf[Map[String, A]])
  }

  private def makeAuthHeader(token: AuthToken): Authorization = {
    headers.Authorization(OAuth2BearerToken(token.value))
  }

  def encodeUri(path: String): String = {
    val pattern = """(https?)?:\/{2}+([\dA-z_.-]+)+(:[\d]+)??(\/[~0-9A-z\#\+\%@\.\/_ -]+)(\?[0-9A-z\+\%@\/&\[\];=_-]+)?""".r

    def toUri(url: String) = url match {
      case pattern(theScheme, theHost, thePort, thePath, theParams) =>
        val p: Int = Try(thePort.replace(":","").toInt).toOption.getOrElse(0)
        val qp: Option[String] = Try(theParams.replace("?","")).toOption
        Uri.from(scheme = theScheme, port = p, host = theHost, path = thePath, queryString = qp)
    }
    toUri(path).toString
  }

  private def sendRequest(httpRequest: HttpRequest): HttpResponse = {
    val responseFuture = retryExponentially() {
      () => Http().singleRequest(request = httpRequest).map { response =>
        // retry any 401 or 500 errors - this is because we have seen the proxy get backend errors
        // from google querying for token info which causes a 401 if it is at the level if the
        // service being directly called or a 500 if it happens at a lower level service
        if (response.status == StatusCodes.Unauthorized || response.status == StatusCodes.InternalServerError) {
          throwRestException(response)
        } else {
          response
        }
      }
    }
    Await.result(responseFuture, 5.minutes)
  }

  def extractResponseString(response: HttpResponse): String = {
    val responseStringFuture: Future[String] = response.entity.toStrict(5 minutes).map(_.data.utf8String)
    Await.result(responseStringFuture, 5 minutes)
  }

  def parseResponse(response: HttpResponse): String = {
    response.status.isSuccess() match {
      case true =>
        extractResponseString(response)
      case _ =>
        logger.error(extractResponseString(response)) // write to test log
        throwRestException(response)
    }
  }

  private def throwRestException(response: HttpResponse) = {
    throw RestException(extractResponseString(response))
  }

  import scala.reflect.{ClassTag, classTag}
  def parseResponseAs[T: ClassTag](response: HttpResponse): T = {
    // https://stackoverflow.com/questions/6200253/scala-classof-for-type-parameter
    val classT: Class[T] = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    mapper.readValue(parseResponse(response), classT)
  }

  // return Some(T) on success, None on failure
  def parseResponseOption[T: ClassTag](response: HttpResponse): Option[T] = {
    if (response.status.isSuccess())
      Option(parseResponseAs[T](response))
    else
      None
  }

  private def requestWithJsonContent(method: HttpMethod, uri: String, content: Any, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    val req = HttpRequest(method, encodeUri(uri), List(makeAuthHeader(token)) ++ httpHeaders, HttpEntity(ContentTypes.`application/json`, mapper.writeValueAsString(content)))
    parseResponse(sendRequest(req))
  }

  def postRequestWithMultipart(uri:String, name: String, content: String)(implicit token: AuthToken): String = {
    val part = Multipart.FormData.BodyPart.Strict(name, HttpEntity(ByteString(content)))
    val formData = Multipart.FormData(Source.single(part))
    val req = HttpRequest(POST, encodeUri(uri), List(makeAuthHeader(token)), formData.toEntity())
    parseResponse(sendRequest(req))
  }

  private def requestBasic(method: HttpMethod, uri: String, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): HttpResponse = {
    val req = HttpRequest(method, encodeUri(uri), List(makeAuthHeader(token)) ++ httpHeaders)
    sendRequest(req)
  }

  def patchRequest(uri: String, content: Any, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestWithJsonContent(PATCH, uri, content, httpHeaders)
  }

  def postRequest(uri: String, content: Any = None, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestWithJsonContent(POST, uri, content, httpHeaders)
  }

  def putRequest(uri: String, content: Any = None, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestWithJsonContent(PUT, uri, content, httpHeaders)
  }

  def deleteRequest(uri: String, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestWithJsonContent(DELETE, uri, None, httpHeaders)
  }

  def deleteRequestWithContent(uri: String, content: Any = None, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestWithJsonContent(DELETE, uri, content, httpHeaders)
  }

  def getRequest(uri: String, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): HttpResponse = {
    requestBasic(GET, uri, httpHeaders)
  }
}
