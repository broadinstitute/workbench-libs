package org.broadinstitute.dsde.workbench.openTelemetry

import cats.ApplicativeError
import cats.effect._

import scala.concurrent.duration.Duration

object FakeOpenTelemetryMetricsInterpreter extends OpenTelemetryMetrics[IO] {
  def time[A](name: String)(fa: IO[A])(implicit timer: Timer[IO], ae: ApplicativeError[IO, Throwable]): IO[A] = fa

  def gauge[A](name: String, value: Double): IO[Unit] = IO.unit

  def incrementCounter[A](name: String, count: Long = 1): IO[Unit] = IO.unit

  def recordDuration(name: String, duration: Duration)(implicit timer: Timer[IO]): IO[Unit] = IO.unit
}
