package org.broadinstitute.dsde.workbench.newrelic
package mock

import cats.effect._

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

object FakeNewRelicMetricsInterpreter extends NewRelicMetrics {
  def timeIO[A](name: String, reportError: Boolean = false)(ioa: IO[A])(implicit timer: Timer[IO]): IO[A] = ioa

  def timeFuture[A](name: String, reportError: Boolean = false)(futureA: => Future[A])(implicit ec: ExecutionContext): Future[A] = futureA

  def gauge[A](name: String, value: Float): IO[Unit] = IO.unit

  def incrementCounterIO[A](name: String, count: Int = 1): IO[Unit] = IO.unit

  def incrementCounterFuture[A](name: String, count: Int = 1)(implicit ec: ExecutionContext): Future[Unit] = Future.unit

  override def recordResponseTimeIO(name: String, duration: Duration): IO[Unit] = IO.unit

  override def recordResponseTimeFuture(name: String, duration: Duration)(implicit ec: ExecutionContext): Future[Unit] = Future.unit
}
