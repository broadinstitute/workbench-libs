package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayOutputStream, IOException, InputStream}
import java.util.concurrent.TimeUnit
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.{HttpResponseException => GoogleHttpResponseException}
import com.google.api.client.http.{HttpResponse => GoogleHttpResponse}
import com.google.api.client.http.json.JsonHttpContent
import com.typesafe.scalalogging.LazyLogging
import net.logstash.logback.argument.StructuredArguments
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumented.GoogleCounters
import org.broadinstitute.dsde.workbench.metrics.{GoogleInstrumented, Histogram, InstrumentedRetry}
import org.broadinstitute.dsde.workbench.model.ErrorReport
import spray.json.{JsValue, RootJsonFormat}

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.util.{Failure, Success, Try}

/**
 * Created by mbemis on 5/10/16.
 */
//These predicates are for use in retries.
//To use them, import GoogleUtilities.Predicates._
object GoogleUtilities {
  object RetryPredicates {
    def when5xx(throwable: Throwable): Boolean = throwable match {
      case t: GoogleHttpResponseException => t.getStatusCode / 100 == 5
      case _                              => false
    }

    def whenUsageLimited(throwable: Throwable): Boolean = throwable match {
      case t: GoogleJsonResponseException =>
        (t.getStatusCode == 403 || t.getStatusCode == 429) &&
          compareErrorDomain(t)(_.equalsIgnoreCase("usageLimits"))
      case _ => false
    }

    def whenGlobalUsageLimited(throwable: Throwable): Boolean = throwable match {
      case t: GoogleJsonResponseException =>
        (t.getStatusCode == 403 || t.getStatusCode == 429) &&
          compareErrorDomain(t)(_.equalsIgnoreCase("global"))
      case _ => false
    }

    def when404(throwable: Throwable): Boolean = throwable match {
      case t: GoogleHttpResponseException => t.getStatusCode == 404
      case _                              => false
    }

    def whenInvalidValueOnBucketCreation(throwable: Throwable): Boolean = throwable match {
      case t: GoogleJsonResponseException =>
        t.getStatusCode == 400 &&
          compareErrorReason(t)(_.equalsIgnoreCase("invalid"))
      case _ => false
    }

    /**
     * Think twice about reaching for this predicate in a retry. Usually, 412 Precondition Failed is an indication of an ETag mismatch.
     * Typically, doing a GET on a GCP resource will return an ETag along with the response. ETags are a form of concurrency control: they are updated
     * each time a resource is modified. The expectation is that you first GET the resource, and when modifying it, you pass along the same ETag
     * that you got. If the ETag you provide matches the one on Google's side, the modification is applied and your request succeeds. if it doesn't,
     * Google returns 412 Precondition Failed to tell you that someone else has modified the object from under you. The correct response is usually to
     * try the whole operation again, starting from the GET to get both the new ETag and the updated object.
     *
     * If you want to make sure your modification goes through regardless of whether any updates have been applied, you can pass the special ETag *.
     * But that is a very stompy thing to do and you should proceed with extreme caution.
     *
     * In most cases, either of the approaches outlined above are preferable to using this predicate. The result of using this predicate will be that
     * your request is retried in its entirety. It does not handle re-GET-ting the resource for you and updating the ETag. Therefore, you should only
     * use this predicate when you are seeing 412 Precondition Failed and the API does not permit passing ETags in your HTTP request. This is rare but
     * does occasionally occur, most often during DELETE calls, which do not pass a request body and therefore have no ETags for you to set.
     */
    def whenPreconditionFailed(throwable: Throwable): Boolean = throwable match {
      case t: GoogleHttpResponseException => t.getStatusCode == 412
      case _                              => false
    }

    def whenNonHttpIOException(throwable: Throwable): Boolean = throwable match {
      //NOTE Google exceptions are subclasses of IO, so without the two false cases at the top, this would
      //match on ANY non-2xx Google response.
      case _: GoogleJsonResponseException => false
      case _: GoogleHttpResponseException => false
      case _: IOException                 => true
      case _                              => false
    }

    def when409(throwable: Throwable): Boolean = throwable match {
      case t: GoogleHttpResponseException => t.getStatusCode == 409
      case _                              => false
    }

    private def compareErrorDomain(ex: GoogleJsonResponseException)(fn: String => Boolean): Boolean = {
      val res = for {
        details <- Option(ex.getDetails)
        errors <- Option(details.getErrors).map(_.asScala)
        headError <- errors.headOption
        domain <- Option(headError.getDomain)
      } yield fn(domain)
      res.getOrElse(false)
    }

    private def compareErrorReason(ex: GoogleJsonResponseException)(fn: String => Boolean): Boolean = {
      val res = for {
        details <- Option(ex.getDetails)
        errors <- Option(details.getErrors).map(_.asScala)
        headError <- errors.headOption
        reason <- Option(headError.getReason)
      } yield fn(reason)
      res.getOrElse(false)
    }
  }
}

trait GoogleUtilities extends LazyLogging with InstrumentedRetry with GoogleInstrumented {
  implicit val executionContext: ExecutionContext

  //FIXME: when we finally remove this, also remove the @silent annotation from the top of GoogleUtilitiesSpec.scala
  @deprecated(
    message =
      "This predicate is complicated and almost certainly doesn't do what you mean. Favor use of retry() and retryWithRecover() with explicitly defined predicates instead. There are some useful predicates at the top of GoogleUtilities; try importing GoogleUtilities.Predicates._",
    since = "workbench-google 0.20"
  )
  protected def when500orGoogleError(throwable: Throwable): Boolean =
    throwable match {
      case t: GoogleJsonResponseException =>
        ((t.getStatusCode == 403 || t.getStatusCode == 429) && t.getDetails.getErrors.asScala.head.getDomain
          .equalsIgnoreCase("usageLimits")) ||
          (t.getStatusCode == 400 && t.getDetails.getErrors.asScala.head.getReason.equalsIgnoreCase("invalid")) ||
          t.getStatusCode == 404 ||
          t.getStatusCode / 100 == 5
      case t: GoogleHttpResponseException => t.getStatusCode / 100 == 5
      case _: IOException                 => true
      case _                              => false
    }

  @deprecated(
    message =
      "This function relies on a complicated predicate that almost certainly doesn't do what you mean. Use retry() with explicitly defined predicates instead. There are some useful predicates at the top of GoogleUtilities; try importing GoogleUtilities.Predicates._",
    since = "workbench-google 0.20"
  )
  protected def retryWhen500orGoogleError[T](op: () => T)(implicit histo: Histogram): Future[T] =
    retryExponentially(when500orGoogleError)(() => Future(blocking(op())))

  protected def combine(predicates: Seq[Throwable => Boolean]): (Throwable => Boolean) = { throwable =>
    predicates.map(_(throwable)).foldLeft(false)(_ || _)
  }

  //Retry if any of the predicates return true.
  protected def retry[T](predicates: (Throwable => Boolean)*)(op: () => T)(implicit histo: Histogram): Future[T] =
    retryExponentially(combine(predicates))(() => Future(blocking(op())))

  @deprecated(
    message =
      "This function relies on a complicated predicate that almost certainly doesn't do what you mean. Use retryWithRecover() with explicitly defined predicates instead. There are some useful predicates at the top of GoogleUtilities; try importing GoogleUtilities.Predicates._",
    since = "workbench-google 0.20"
  )
  protected def retryWithRecoverWhen500orGoogleError[T](
    op: () => T
  )(recover: PartialFunction[Throwable, T])(implicit histo: Histogram): Future[T] =
    retryExponentially(when500orGoogleError)(() => Future(blocking(op())).recover(recover))

  //Retry if any of the predicates return true.
  protected def retryWithRecover[T](
    predicates: (Throwable => Boolean)*
  )(op: () => T)(recover: PartialFunction[Throwable, T])(implicit histo: Histogram): Future[T] =
    retryExponentially(combine(predicates))(() => Future(blocking(op())).recover(recover))

  // $COVERAGE-OFF$Can't test Google request code. -hussein
  protected def executeGoogleRequest[T](request: AbstractGoogleClientRequest[T])(implicit counters: GoogleCounters): T =
    executeGoogleCall(request) { response =>
      response.parseAs(request.getResponseClass)
    }

  protected def executeGoogleFetch[A, B](
    request: AbstractGoogleClientRequest[A]
  )(f: (InputStream) => B)(implicit counters: GoogleCounters): B =
    executeGoogleCall(request) { response =>
      val stream = response.getContent
      try f(stream)
      finally stream.close()
    }

  protected def executeGoogleCall[A, B](
    request: AbstractGoogleClientRequest[A]
  )(processResponse: (GoogleHttpResponse) => B)(implicit counters: GoogleCounters): B = {
    val start = System.currentTimeMillis()
    Try {
      request.executeUnparsed()
    } match {
      case Success(response) =>
        logGoogleRequest(request, start, response)
        instrumentGoogleRequest(request, start, Right(response))
        try processResponse(response)
        finally response.disconnect()
      case Failure(httpRegrets: GoogleHttpResponseException) =>
        logGoogleRequest(request, start, httpRegrets)
        instrumentGoogleRequest(request, start, Left(httpRegrets))
        throw httpRegrets
      case Failure(regrets) =>
        logGoogleRequest(request, start, regrets)
        instrumentGoogleRequest(request, start, Left(regrets))
        throw regrets
    }
  }

  private def logGoogleRequest[A](request: AbstractGoogleClientRequest[A],
                                  startTime: Long,
                                  response: GoogleHttpResponse
  ): Unit =
    logGoogleRequest(request, startTime, Option(response.getStatusCode), None)

  private def logGoogleRequest[A](request: AbstractGoogleClientRequest[A], startTime: Long, regrets: Throwable): Unit =
    regrets match {
      case e: GoogleHttpResponseException => logGoogleRequest(request, startTime, Option(e.getStatusCode), None)
      case t: Throwable                   => logGoogleRequest(request, startTime, None, Option(ErrorReport(t)))
    }

  private def logGoogleRequest[A](request: AbstractGoogleClientRequest[A],
                                  startTime: Long,
                                  statusCode: Option[Int],
                                  errorReport: Option[ErrorReport]
  ): Unit = {
    import spray.json._

    val payload =
      if (logger.underlying.isDebugEnabled) {
        Option(request.getHttpContent) match {
          case Some(content: JsonHttpContent) =>
            Try {
              val outputStream = new ByteArrayOutputStream()
              content.writeTo(outputStream)
              outputStream.toString.parseJson
            }.toOption
          case _ => None
        }
      } else {
        None
      }

    val googleRequest = GoogleRequest(request.getRequestMethod,
                                      request.buildHttpRequestUrl().toString,
                                      payload,
                                      System.currentTimeMillis() - startTime,
                                      statusCode,
                                      errorReport
    )
    logGoogleRequest(googleRequest)
  }

  protected def logGoogleRequest(googleRequest: GoogleRequest): Unit = {
    import GoogleRequestJsonSupport._
    import spray.json._

    val googleRequestJson = googleRequest.toJson(GoogleRequestFormat).compactPrint
    logger.debug(
      googleRequestJson,
      StructuredArguments.raw("googleRequest", googleRequestJson)
    )
  }

  private def instrumentGoogleRequest[A](
    request: AbstractGoogleClientRequest[A],
    startTime: Long,
    responseOrException: Either[Throwable, com.google.api.client.http.HttpResponse]
  )(implicit counters: GoogleCounters): Unit = {
    val (counter, timer) = counters(request, responseOrException)
    counter += 1
    timer.update(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
  }

  // $COVERAGE-ON$
}

protected[google] case class GoogleRequest(method: String,
                                           url: String,
                                           payload: Option[JsValue],
                                           time_ms: Long,
                                           statusCode: Option[Int],
                                           errorReport: Option[ErrorReport]
)
protected[google] object GoogleRequestJsonSupport {
  import spray.json.DefaultJsonProtocol._
  import org.broadinstitute.dsde.workbench.model.ErrorReportJsonSupport._

  implicit val GoogleRequestFormat = jsonFormat6(GoogleRequest)
}
