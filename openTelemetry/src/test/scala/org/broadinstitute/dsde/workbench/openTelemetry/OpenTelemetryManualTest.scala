package org.broadinstitute.dsde.workbench.openTelemetry

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.opencensus.trace.Tracing
import io.opencensus.trace.samplers.Samplers

import java.nio.file.Paths

object OpenTelemetryManualTest {
  private def test(): IO[Unit] = IO {
    val tracer = Tracing.getTracer()
    val ss = tracer
      .spanBuilder("qi")
      .setSampler(Samplers.alwaysSample())
    val span = ss.startSpan()
    span.addAnnotation("initial work")
    Thread.sleep(220)
    span.addAnnotation("Finished initial work")

    span.end()
    println("finished")
  }

  def run(): Unit = {
    val res = OpenTelemetryMetrics
      .registerTracing[IO](Paths.get("/Users/qi/workspace/leonardo/config/leonardo-account.json"))
      .use(_ => test())

    res.unsafeRunSync()
  }
}
