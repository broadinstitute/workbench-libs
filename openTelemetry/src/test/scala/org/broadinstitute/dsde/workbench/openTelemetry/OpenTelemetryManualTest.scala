package org.broadinstitute.dsde.workbench.openTelemetry

import java.nio.file.Paths

import cats.effect.IO
import io.opencensus.trace.Tracing
import io.opencensus.trace.samplers.Samplers
import scala.concurrent.ExecutionContext.global

object OpenTelemetryManualTest {
  implicit val cs = IO.contextShift(global)

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
    val blocker = Blocker.liftExecutionContext(global)
    val res = OpenTelemetryMetrics
      .registerTracing[IO](Paths.get("/Users/qi/workspace/leonardo/config/leonardo-account.json"), blocker)
      .use(_ => test())

    res.unsafeRunSync()
  }
}
