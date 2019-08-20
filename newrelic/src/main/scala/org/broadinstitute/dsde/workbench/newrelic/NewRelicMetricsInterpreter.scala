package org.broadinstitute.dsde.workbench.newrelic

import java.util.concurrent.TimeUnit

import cats.effect.{IO, Timer}
import cats.implicits._
import com.newrelic.api.agent.NewRelic

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class NewRelicMetricsInterpreter(appName: String) extends NewRelicMetrics {
  val prefix = s"Custom/$appName"

  def timeIO[A](name: String, reportError: Boolean = false)(ioa: IO[A])(implicit timer: Timer[IO]): IO[A] =
    for {
      start <- timer.clock.monotonic(TimeUnit.MILLISECONDS)
      attemptedResult <- ioa.attempt
      end <- timer.clock.monotonic(TimeUnit.MILLISECONDS)
      duration = end - start
      _ <- attemptedResult match {
        case Left(e) =>
          for {
            _ <- IO(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}/failure", duration))
            _ <- if(reportError) IO(NewRelic.noticeError(e)) else IO.unit
          } yield ()
        case Right(_) => IO(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}/success", duration))
      }
      res <- IO.fromEither(attemptedResult)
    } yield res

  def timeFuture[A](name: String, reportError: Boolean = false)(futureA: => Future[A])(implicit ec: ExecutionContext): Future[A] =
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

  def gauge[A](name: String, value: Float): IO[Unit] = IO(NewRelic.recordMetric(s"${prefix}/${name}", value))

  def incrementCounterIO[A](name: String, count: Int = 1): IO[Unit] = IO(NewRelic.incrementCounter(s"${prefix}/${name}", count))

  def incrementCounterFuture[A](name: String, count: Int = 1)(implicit ec: ExecutionContext): Future[Unit] = Future(NewRelic.incrementCounter(s"${prefix}/${name}", count))

  def recordResponseTimeIO(name: String, duration: Duration): IO[Unit] = IO(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}", duration.toMillis))

  def recordResponseTimeFuture(name: String, duration: Duration)(implicit ec: ExecutionContext) = Future(NewRelic.recordResponseTimeMetric(s"${prefix}/${name}", duration.toMillis))
}
