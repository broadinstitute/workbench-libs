package org.broadinstitute.dsde.workbench.newrelic

import java.util.concurrent.TimeUnit

import cats.ApplicativeError
import cats.effect.{Async, Timer}
import cats.implicits._
import com.newrelic.api.agent.NewRelic

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class NewRelicMetricsInterpreter[F[_]: Async](appName: String) extends NewRelicMetrics[F] {
  val prefix = s"Custom/$appName"

  def timeIO[A](name: String, reportError: Boolean = false)(fa: F[A])(implicit timer: Timer[F],
                                                                      ae: ApplicativeError[F, Throwable]): F[A] =
    for {
      start <- timer.clock.monotonic(TimeUnit.MILLISECONDS)
      attemptedResult <- fa.attempt(ae)
      end <- timer.clock.monotonic(TimeUnit.MILLISECONDS)
      duration = end - start
      _ <- attemptedResult match {
        case Left(e) =>
          for {
            _ <- Async[F].delay(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}/failure", duration))
            _ <- if (reportError) Async[F].delay[Unit](NewRelic.noticeError(e)) else Async[F].unit
          } yield ()
        case Right(_) => Async[F].delay(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}/success", duration))
      }
      res <- Async[F].fromEither(attemptedResult)
    } yield res

  def timeFuture[A](name: String,
                    reportError: Boolean = false)(futureA: => Future[A])(implicit ec: ExecutionContext): Future[A] =
    for {
      start <- Future(System.currentTimeMillis())
      attemptedResult <- futureA.attempt
      end <- Future(System.currentTimeMillis())
      duration = end - start
      _ <- attemptedResult match {
        case Left(e) =>
          for {
            _ <- Future(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}/failure", duration))
            _ <- if (reportError) Future(NewRelic.noticeError(e)) else Future.unit
          } yield ()
        case Right(_) => Future(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}/success", duration))
      }
      res <- Future.fromTry(attemptedResult.toTry)
    } yield res

  def gauge[A](name: String, value: Float): F[Unit] = Async[F].delay(NewRelic.recordMetric(s"${prefix}/${name}", value))

  def incrementCounter[A](name: String, count: Int = 1): F[Unit] =
    Async[F].delay(NewRelic.incrementCounter(s"${prefix}/${name}", count))

  def incrementCounterFuture[A](name: String, count: Int = 1)(implicit ec: ExecutionContext): Future[Unit] =
    Future(NewRelic.incrementCounter(s"${prefix}/${name}", count))

  def recordResponseTime(name: String, duration: Duration): F[Unit] =
    Async[F].delay(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}", duration.toMillis))

  def recordResponseTimeFuture(name: String, duration: Duration)(implicit ec: ExecutionContext) =
    Future(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}", duration.toMillis))
}
