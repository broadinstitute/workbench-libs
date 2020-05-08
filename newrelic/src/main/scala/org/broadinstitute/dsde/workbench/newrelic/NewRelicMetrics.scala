package org.broadinstitute.dsde.workbench.newrelic

import cats.ApplicativeError
import cats.effect.{Async, Timer}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

trait NewRelicMetrics[F[_]] {
  def timeIO[A](name: String, reportError: Boolean = false)(fa: F[A])(implicit timer: Timer[F],
                                                                      ae: ApplicativeError[F, Throwable]): F[A]

  def timeFuture[A](name: String, reportError: Boolean = false)(futureA: => Future[A])(
    implicit ec: ExecutionContext
  ): Future[A]

  def gauge[A](name: String, value: Float): F[Unit]

  def incrementCounter[A](name: String, count: Int = 1): F[Unit]

  def incrementCounterFuture[A](name: String, count: Int = 1)(implicit ec: ExecutionContext): Future[Unit]

  def recordResponseTime(name: String, duration: Duration): F[Unit]

  def recordResponseTimeFuture(name: String, duration: Duration)(implicit ec: ExecutionContext): Future[Unit]
}

object NewRelicMetrics {
  def fromNewRelic[F[_]: Async](appName: String): NewRelicMetrics[F] =
    new NewRelicMetricsInterpreter[F](appName)

  def apply[F[_]](implicit ev: NewRelicMetrics[F]): NewRelicMetrics[F] = ev
}
