package org.broadinstitute.dsde.workbench.metrics

import scala.concurrent.duration._

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives.{extractRequest, mapResponse}

trait InstrumentationDirectives extends WorkbenchInstrumented {
  def instrumentRequest: Directive0 = extractRequest flatMap { request =>
    val timeStamp = System.currentTimeMillis
    mapResponse { response =>
      httpRequestCounter(ExpandedMetricBuilder.empty)(request, response).inc()
      httpRequestTimer(ExpandedMetricBuilder.empty)(request, response).update(System.currentTimeMillis - timeStamp, MILLISECONDS)
      response
    }
  }
}
