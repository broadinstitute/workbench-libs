package org.broadinstitute.dsde.workbench.model

import akka.http.scaladsl.model.StatusCodes
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.json._

import ErrorReportJsonSupport._

class ErrorReportSpec extends FlatSpecLike with BeforeAndAfterAll with Matchers {
  "ErrorReport" should "roundtrip json" in {
    implicit val errorReportSource = ErrorReportSource("test")
    val internalEr = ErrorReport("internalERMessage")
    val stacktrace = Seq(new StackTraceElement("testClass", "testMethod", "fileName", 42))
    val er = ErrorReport("testMessage", Some(StatusCodes.OK), Seq(internalEr), stacktrace, Some(classOf[RuntimeException]))

    val erJson = er.toJson
    val erRead = erJson.convertTo[ErrorReport]

    erRead shouldBe er
  }
}
