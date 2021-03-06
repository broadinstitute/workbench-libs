package org.broadinstitute.dsde.workbench.metrics

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.{HttpResponse, HttpResponseException}
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumented._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService._

/**
 * Mixin trait for Google instrumentation.
 */
trait GoogleInstrumented extends WorkbenchInstrumented {
  final val GoogleServiceMetricKey = "googleService"

  implicit protected def googleCounters(implicit service: GoogleInstrumentedService): GoogleCounters =
    (request, responseOrException) => {
      val base = ExpandedMetricBuilder
        .expand(GoogleServiceMetricKey, service)
        .expand(HttpRequestMethodMetricKey, request.getRequestMethod.toLowerCase)
      val baseWithStatusCode =
        extractStatusCode(responseOrException).map(s => base.expand(HttpResponseStatusCodeMetricKey, s)).getOrElse(base)
      val counter = baseWithStatusCode.asCounter("request")
      val timer = baseWithStatusCode.asTimer("latency")
      (counter, timer)
    }

  implicit protected def googleRetryHistogram(implicit service: GoogleInstrumentedService): Histogram =
    ExpandedMetricBuilder
      .expand(GoogleServiceMetricKey, service)
      .asHistogram("retry")
}

object GoogleInstrumented {
  type GoogleCounters = (AbstractGoogleClientRequest[_], Either[Throwable, HttpResponse]) => (Counter, Timer)

  private def extractStatusCode(responseOrException: Either[Throwable, HttpResponse]): Option[Int] =
    responseOrException match {
      case Left(t: HttpResponseException) => Some(t.getStatusCode)
      case Left(_)                        => None
      case Right(response)                => Some(response.getStatusCode)
    }
}
