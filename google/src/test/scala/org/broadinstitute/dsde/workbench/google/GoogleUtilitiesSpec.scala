package org.broadinstitute.dsde.workbench.google

import java.io.IOException

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo
import com.google.api.client.googleapis.json.{GoogleJsonError, GoogleJsonResponseException}
import com.google.api.client.http._
import org.broadinstitute.dsde.workbench.google.GoogleUtilities.RetryPredicates._
import org.broadinstitute.dsde.workbench.metrics.{Histogram, StatsDTestUtils}
import org.broadinstitute.dsde.workbench.util.MockitoTestUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import spray.json._

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

//FIXME: Remove commented out tests once we remove retryWhen500orGoogleError.
class GoogleUtilitiesSpec
    extends TestKit(ActorSystem("MySpec"))
    with GoogleUtilities
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures
    with Eventually
    with MockitoTestUtils
    with StatsDTestUtils {
  implicit val executionContext = ExecutionContext.global
  implicit def histo: Histogram = ExpandedMetricBuilder.empty.asHistogram("histo")

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  // a total of 4 attempts (include the first one that has no delay)
  override def exponentialBackOffIntervals = Seq(10 milliseconds, 20 milliseconds, 40 milliseconds)

  def buildHttpResponseException(statusCode: Int): HttpResponseException =
    new HttpResponseException.Builder(statusCode, null, new HttpHeaders()).build

  def buildGoogleJsonResponseException(statusCode: Int,
                                       message: Option[String] = None,
                                       reason: Option[String] = None,
                                       domain: Option[String] = None
  ): GoogleJsonResponseException = {
    val httpExc = new HttpResponseException.Builder(statusCode, null, new HttpHeaders())
    val errInfo = new ErrorInfo()

    message foreach httpExc.setMessage
    message foreach errInfo.setMessage
    reason foreach errInfo.setReason
    domain foreach errInfo.setDomain

    val gjError = new GoogleJsonError()
    gjError.setErrors(Seq(errInfo).asJava)
    new GoogleJsonResponseException(httpExc, gjError)
  }

  class Counter() {
    var counter = 0

    def alwaysBoom(): Int = {
      counter += 1
      throw new IOException("alwaysBoom")
    }

    def boomOnce(): Int = {
      counter += 1
      if (counter > 1) {
        42
      } else {
        throw new IOException("boomOnce")
      }
    }

    def httpBoom(): Int = {
      counter += 1
      throw buildHttpResponseException(503)
    }
  }

  "GoogleUtilities.Predicates" should "return true in positive cases" in {
    when5xx(buildGoogleJsonResponseException(500)) shouldBe true
    when5xx(buildHttpResponseException(502)) shouldBe true

    whenUsageLimited(buildGoogleJsonResponseException(403, None, None, Some("usageLimits"))) shouldBe true
    whenUsageLimited(buildGoogleJsonResponseException(429, None, None, Some("usageLimits"))) shouldBe true

    whenGlobalUsageLimited(buildGoogleJsonResponseException(429, None, None, Some("global"))) shouldBe true

    when404(buildGoogleJsonResponseException(404)) shouldBe true
    when404(buildHttpResponseException(404)) shouldBe true

    whenInvalidValueOnBucketCreation(buildGoogleJsonResponseException(400, None, Some("invalid"), None)) shouldBe true

    whenNonHttpIOException(new IOException("boom")) shouldBe true

    when409(buildGoogleJsonResponseException(409)) shouldBe true

    whenGroupDoesNotExist(buildGoogleJsonResponseException(400, Some("does not exist"), None, None)) shouldBe true
  }

  it should "return false in negative cases" in {
    when5xx(buildGoogleJsonResponseException(400)) shouldBe false
    when5xx(new IOException("boom")) shouldBe false

    whenUsageLimited(buildGoogleJsonResponseException(403, None, None, Some("boom"))) shouldBe false
    whenUsageLimited(buildGoogleJsonResponseException(429, None, None, Some("boom"))) shouldBe false
    whenUsageLimited(buildGoogleJsonResponseException(400)) shouldBe false
    whenUsageLimited(new IOException("boom")) shouldBe false

    whenGlobalUsageLimited(buildGoogleJsonResponseException(403, None, None, Some("boom"))) shouldBe false
    whenGlobalUsageLimited(buildGoogleJsonResponseException(429, None, None, Some("boom"))) shouldBe false
    whenGlobalUsageLimited(buildGoogleJsonResponseException(400)) shouldBe false
    whenGlobalUsageLimited(new IOException("boom")) shouldBe false

    when404(buildGoogleJsonResponseException(403)) shouldBe false
    when404(buildHttpResponseException(403)) shouldBe false

    whenInvalidValueOnBucketCreation(buildGoogleJsonResponseException(400, None, Some("boom"), None)) shouldBe false
    whenInvalidValueOnBucketCreation(buildHttpResponseException(403)) shouldBe false
    whenInvalidValueOnBucketCreation(new IOException("boom")) shouldBe false

    whenNonHttpIOException(buildHttpResponseException(404)) shouldBe false
  }

//  "when500orGoogleError" should "return true for 500 or Google errors" in {
//    when500orGoogleError(buildGoogleJsonResponseException(403, None, None, Some("usageLimits"))) shouldBe true
//    when500orGoogleError(buildGoogleJsonResponseException(429, None, None, Some("usageLimits"))) shouldBe true
//    when500orGoogleError(buildGoogleJsonResponseException(400, None, Some("invalid"), None)) shouldBe true
//    when500orGoogleError(buildGoogleJsonResponseException(404)) shouldBe true
//
//    when500orGoogleError(buildGoogleJsonResponseException(500)) shouldBe true
//    when500orGoogleError(buildGoogleJsonResponseException(502)) shouldBe true
//    when500orGoogleError(buildGoogleJsonResponseException(503)) shouldBe true
//
//    when500orGoogleError(buildHttpResponseException(500)) shouldBe true
//    when500orGoogleError(buildHttpResponseException(502)) shouldBe true
//    when500orGoogleError(buildHttpResponseException(503)) shouldBe true
//
//    when500orGoogleError(new IOException("boom")) shouldBe true
//  }

//  it should "return false otherwise" in {
//    when500orGoogleError(buildGoogleJsonResponseException(400, None, Some("boom"), None)) shouldBe false
//    when500orGoogleError(buildGoogleJsonResponseException(401)) shouldBe false
//    when500orGoogleError(buildGoogleJsonResponseException(403, Some("boom"), None, Some("boom"))) shouldBe false
//    when500orGoogleError(buildGoogleJsonResponseException(429, None, None, Some("boom"))) shouldBe false
//
//    when500orGoogleError(buildHttpResponseException(400)) shouldBe false
//    when500orGoogleError(buildHttpResponseException(401)) shouldBe false
//    when500orGoogleError(buildHttpResponseException(403)) shouldBe false
//  }

//  "retryWhen500orGoogleError" should "retry once per backoff interval and then fail" in {
//    withStatsD {
//      val counter = new Counter()
//      whenReady(retryWhen500orGoogleError(() => counter.alwaysBoom()).failed) { f =>
//        f shouldBe a[IOException]
//        counter.counter shouldBe 4 //extra one for the first attempt
//      }
//    } { capturedMetrics =>
//      capturedMetrics should contain("test.histo.samples" -> "1")
//      capturedMetrics should contain("test.histo.max" -> "4") // 4 exceptions
//    }
//  }
//
//  it should "not retry after a success" in {
//    withStatsD {
//      val counter = new Counter()
//      whenReady(retryWhen500orGoogleError(() => counter.boomOnce())) { s =>
//        s shouldBe 42
//        counter.counter shouldBe 2
//      }
//    } { capturedMetrics =>
//      capturedMetrics should contain("test.histo.samples" -> "1")
//      capturedMetrics should contain("test.histo.max" -> "1") // 1 exception
//    }
//  }

  "combine" should "combine predicates correctly" in {
    combine(Seq(whenNonHttpIOException, when5xx))(new IOException("boom")) shouldBe true
    combine(Seq(whenNonHttpIOException, when5xx))(buildHttpResponseException(502)) shouldBe true

    combine(Seq(whenNonHttpIOException, when5xx))(buildHttpResponseException(400)) shouldBe false

    combine(Seq.empty)(new IOException("boom")) shouldBe false
  }

  "retry" should "retry once per backoff interval and then fail" in {
    withStatsD {
      val counter = new Counter()
      whenReady(retry(whenNonHttpIOException(_))(() => counter.alwaysBoom()).failed) { f =>
        f shouldBe a[IOException]
        counter.counter shouldBe 4 // extra one for the first attempt
      }
    } { capturedMetrics =>
      capturedMetrics should contain("test.histo.samples" -> "1")
      capturedMetrics should contain("test.histo.max" -> "4") // 4 exceptions
    }
  }

  it should "not retry after a success" in {
    withStatsD {
      val counter = new Counter()
      whenReady(retry(whenNonHttpIOException(_))(() => counter.boomOnce())) { s =>
        s shouldBe 42
        counter.counter shouldBe 2
      }
    } { capturedMetrics =>
      capturedMetrics should contain("test.histo.samples" -> "1")
      capturedMetrics should contain("test.histo.max" -> "1") // 1 exception
    }
  }

//  "retryWithRecoverWhen500orGoogleError" should "stop retrying if it recovers" in {
//    withStatsD {
//      val counter = new Counter()
//
//      def recoverIO: PartialFunction[Throwable, Int] = {
//        case _: IOException => 42
//      }
//
//      whenReady(retryWithRecoverWhen500orGoogleError(() => counter.alwaysBoom())(recoverIO)) { s =>
//        s shouldBe 42
//        counter.counter shouldBe 1
//      }
//    } { capturedMetrics =>
//      capturedMetrics should contain("test.histo.samples" -> "1")
//      capturedMetrics should contain("test.histo.max" -> "0") // 0 exceptions
//    }
//  }

//  it should "keep retrying and fail if it doesn't recover" in {
//    withStatsD {
//      val counter = new Counter()
//
//      def recoverHttp: PartialFunction[Throwable, Int] = {
//        case h: HttpResponseException if h.getStatusCode == 404 => 42
//      }
//
//      whenReady(retryWithRecoverWhen500orGoogleError(() => counter.httpBoom())(recoverHttp).failed) { f =>
//        f shouldBe a[HttpResponseException]
//        counter.counter shouldBe 4 //extra one for the first attempt
//      }
//    } { capturedMetrics =>
//      capturedMetrics should contain("test.histo.samples" -> "1")
//      capturedMetrics should contain("test.histo.max" -> "4") // 4 exceptions
//    }
//  }

  "retryWithRecover" should "stop retrying if it recovers" in {
    withStatsD {
      val counter = new Counter()

      def recoverIO: PartialFunction[Throwable, Int] = { case _: IOException =>
        42
      }

      whenReady(retryWithRecover(whenNonHttpIOException(_))(() => counter.alwaysBoom())(recoverIO)) { s =>
        s shouldBe 42
        counter.counter shouldBe 1
      }
    } { capturedMetrics =>
      capturedMetrics should contain("test.histo.samples" -> "1")
      capturedMetrics should contain("test.histo.max" -> "0") // 0 exceptions
    }
  }

  it should "keep retrying and fail if it doesn't recover" in {
    withStatsD {
      val counter = new Counter()

      def recoverHttp: PartialFunction[Throwable, Int] = {
        case h: HttpResponseException if h.getStatusCode == 404 => 42
      }

      whenReady(retryWithRecover(when5xx(_))(() => counter.httpBoom())(recoverHttp).failed) { f =>
        f shouldBe a[HttpResponseException]
        counter.counter shouldBe 4 // extra one for the first attempt
      }
    } { capturedMetrics =>
      capturedMetrics should contain("test.histo.samples" -> "1")
      capturedMetrics should contain("test.histo.max" -> "4") // 4 exceptions
    }
  }
}

class GoogleJsonSpec extends AnyFlatSpecLike with Matchers {
  "GoogleRequest" should "roundtrip json" in {
    import GoogleRequestJsonSupport._
    val gooRq = GoogleRequest("GET", "www.thegoogle.hooray", Some(JsString("you did a search")), 400, Some(200), None)

    val rqJson = gooRq.toJson
    val rqRead = rqJson.convertTo[GoogleRequest]

    rqRead shouldBe gooRq
  }
}
