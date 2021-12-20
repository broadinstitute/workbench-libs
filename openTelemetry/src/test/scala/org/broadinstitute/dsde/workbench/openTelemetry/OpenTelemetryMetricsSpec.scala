package org.broadinstitute.dsde.workbench.openTelemetry

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.net.{HttpURLConnection, URL}

class OpenTelemetryMetricsSpec extends AnyFlatSpecLike with Matchers with WorkbenchTestSuite {
  "the OpenTelemetryMetrics object" should "run a Prometheus endpoint" in {
    val port = 9098
    val res = OpenTelemetryMetrics.exposeMetricsToPrometheus[IO](port).use(_ => IO {
      val connection = new URL(s"http://localhost:${port}/metrics")
        .openConnection()
        .asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(1000)
      connection.setReadTimeout(1000)
      assertResult(200) {
        connection.getResponseCode
      }
    })

    res.unsafeRunSync()
  }
}
