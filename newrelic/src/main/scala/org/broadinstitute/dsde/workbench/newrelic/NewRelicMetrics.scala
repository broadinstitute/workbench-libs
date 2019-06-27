package org.broadinstitute.dsde.workbench.newrelic

import cats.effect.{IO, Timer}

import scala.concurrent.{ExecutionContext, Future}

trait NewRelicMetrics {
  def timeIO[A](name: String, reportError: Boolean = false)(ioa: IO[A])(implicit timer: Timer[IO]): IO[A]

  def timeFuture[A](name: String, reportError: Boolean = false)(futureA: => Future[A])(implicit ec: ExecutionContext): Future[A]

  def gauge[A](name: String, value: Float): IO[Unit]

  def incrementCounterIO[A](name: String, count: Int = 1): IO[Unit]

  def incrementCounterFuture[A](name: String, count: Int = 1)(implicit ec: ExecutionContext): Future[Unit]
}

object NewRelicMetrics {
  def fromNewRelic(appName: String): NewRelicMetrics = new NewRelicMetricsInterpreter(appName)
}
