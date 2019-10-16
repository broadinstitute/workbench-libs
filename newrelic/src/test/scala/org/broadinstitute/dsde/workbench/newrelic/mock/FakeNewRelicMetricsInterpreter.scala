package org.broadinstitute.dsde.workbench.newrelic
package mock

import cats.ApplicativeError
import cats.effect._

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

object FakeNewRelicMetricsInterpreter extends NewRelicMetrics[IO] {
  override def timeIO[A](name: String, reportError: Boolean = false)(ioa: IO[A])(implicit timer: Timer[IO], ae: ApplicativeError[IO, Throwable]): IO[A] = ioa

  override def timeFuture[A](name: String, reportError: Boolean = false)(futureA: => Future[A])(implicit ec: ExecutionContext): Future[A] = futureA

  override def gauge[A](name: String, value: Float): IO[Unit] = IO.unit

  override def incrementCounterFuture[A](name: String, count: Int = 1)(implicit ec: ExecutionContext): Future[Unit] = Future.unit

  override def recordResponseTimeFuture(name: String, duration: Duration)(implicit ec: ExecutionContext): Future[Unit] = Future.unit

  override def incrementCounter[A](name: String, count: Int): IO[Unit] = IO.unit

  override def recordResponseTime(name: String, duration: Duration): IO[Unit] = IO.unit
}
