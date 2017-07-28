package org.broadinstitute.dsde.workbench.model

import akka.http.scaladsl.model.StatusCodes
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.json._

import ErrorReportJsonSupport._

class ErrorReportSpec extends FlatSpecLike with BeforeAndAfterAll with Matchers {
  implicit val errorReportSource = ErrorReportSource("test")

  "json serialization" should "roundtrip" in {
    val internalEr = ErrorReport("internalERMessage")
    val stacktrace = Seq(new StackTraceElement("testClass", "testMethod", "fileName", 42))
    val er = ErrorReport("testMessage", Some(StatusCodes.OK), Seq(internalEr), stacktrace, Some(classOf[RuntimeException]))

    val erJson = er.toJson
    val erRead = erJson.convertTo[ErrorReport]

    erRead shouldBe er
  }

  "message function" should "return exception message if it exists" in {
    val msgExc = new RuntimeException("boom")
    ErrorReport.message(msgExc) shouldBe "boom"
  }

  it should "return exception class if no message exists" in {
    val noMsgExc = new RuntimeException()
    ErrorReport.message(noMsgExc) shouldBe "RuntimeException"
  }

  "causes function" should "return an ErrorReport with no causes if there are none" in {
    val exc = new RuntimeException("boom")
    ErrorReport.causes(exc) shouldBe empty
  }

  it should "return an ErrorReport with suppressed exceptions if there are any" in {
    val excSuppressed = new RuntimeException("suppressed")
    val exc = new RuntimeException("boom")
    exc.addSuppressed(excSuppressed)

    ErrorReport.causes(exc) shouldBe Seq(ErrorReport(excSuppressed))
  }

  it should "return an ErrorReport with suppressed exceptions even if there are other causes" in {
    val excSuppressed = new RuntimeException("suppressed")
    val excCause = new RuntimeException("cause")
    val exc = new RuntimeException("boom", excCause)
    exc.addSuppressed(excSuppressed)

    ErrorReport.causes(exc) shouldBe Seq(ErrorReport(excSuppressed))
  }

  it should "return an ErrorReport with causes if there are no suppressed exceptions" in {
    val excCause = new RuntimeException("cause")
    val exc = new RuntimeException("boom", excCause)

    ErrorReport.causes(exc) shouldBe Seq(ErrorReport(excCause))
  }
}
