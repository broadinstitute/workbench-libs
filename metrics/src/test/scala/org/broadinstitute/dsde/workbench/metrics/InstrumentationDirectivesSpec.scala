package org.broadinstitute.dsde.workbench.metrics

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives.{complete, get, pathEndOrSingleSlash, pathPrefix}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.broadinstitute.dsde.workbench.util.MockitoTestUtils
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class InstrumentationDirectivesSpec
    extends AnyFlatSpecLike
    with InstrumentationDirectives
    with Matchers
    with StatsDTestUtils
    with ScalatestRouteTest
    with Eventually
    with MockitoTestUtils {

  override val workbenchMetricBaseName = "test"

  def testRoute: server.Route =
    instrumentRequest {
      pathPrefix("ping") {
        pathEndOrSingleSlash {
          get {
            complete {
              StatusCodes.OK
            }
          }
        }
      }
    }

  "Instrumentation directives" should "capture metrics" in {
    withStatsD {
      Get("/ping") ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    } { capturedMetrics =>
      val expected = expectedHttpRequestMetrics("get", "ping", StatusCodes.OK.intValue, 1)
      capturedMetrics should contain allElementsOf expected
    }
  }
}
