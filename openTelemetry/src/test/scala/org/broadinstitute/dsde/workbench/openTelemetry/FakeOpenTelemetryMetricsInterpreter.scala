package org.broadinstitute.dsde.workbench.openTelemetry

import cats.ApplicativeError
import cats.effect._

import scala.concurrent.duration.FiniteDuration

object FakeOpenTelemetryMetricsInterpreter extends OpenTelemetryMetrics[IO] {
  def time[A](name: String, distributionBucket: List[FiniteDuration], tags: Map[String, String] = Map.empty)(
    fa: IO[A]
  )(implicit timer: Timer[IO], ae: ApplicativeError[IO, Throwable]): IO[A] = fa

  def gauge[A](name: String, value: Double, tags: Map[String, String] = Map.empty): IO[Unit] = IO.unit

  def incrementCounter[A](name: String, count: Long = 1, tags: Map[String, String] = Map.empty): IO[Unit] = IO.unit

  def recordDuration(name: String,
                     duration: FiniteDuration,
                     distributionBucket: List[FiniteDuration],
                     tags: Map[String, String] = Map.empty)(implicit timer: Timer[IO]): IO[Unit] = IO.unit
}
