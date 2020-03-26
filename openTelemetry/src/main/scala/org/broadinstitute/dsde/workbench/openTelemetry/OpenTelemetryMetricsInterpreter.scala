package org.broadinstitute.dsde.workbench.openTelemetry

import java.util
import java.util.concurrent.TimeUnit

import cats.ApplicativeError
import cats.effect.{Async, Timer}
import cats.implicits._
import io.opencensus.stats.Aggregation.Distribution
import io.opencensus.stats.Measure.{MeasureDouble, MeasureLong}
import io.opencensus.stats.View.Name
import io.opencensus.stats.{Aggregation, BucketBoundaries, Stats, View}
import io.opencensus.tags.{TagKey, TagValue, Tags}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

class OpenTelemetryMetricsInterpreter[F[_]](appName: String)(implicit F: Async[F]) extends OpenTelemetryMetrics[F] {
  private val viewManager = Stats.getViewManager
  private val statsRecorder = Stats.getStatsRecorder
  private val appTagKey =  TagKey.create("app")

  private val appTagValue = TagValue.create(appName)

  private val tagger = Tags.getTagger()
  val tagContext = tagger.emptyBuilder()
    .putLocal(appTagKey, appTagValue)
    .build()

  def time[A](name: String)(fa: F[A])(implicit timer: Timer[F], ae: ApplicativeError[F, Throwable]): F[A] = {
    val latencySuccess =  MeasureDouble.create(s"$name/success", "The successful io latency in milliseconds", "ms")
    val countFailure =  MeasureLong.create(s"$name", s"count of ${name}", "1")

    val latencyDistribution =
      Distribution.create(
        BucketBoundaries.create(
          util.Arrays.asList(
            // Latency in buckets: [>=0ms, >=100ms, >=200ms, >=400ms, >=1s, >=2s, >=4s]
            0.0, 100.0, 200.0, 400.0, 1000.0, 2000.0, 4000.0)))

    val viewSuccessName = Name.create(s"${name}_success_latency")
    val viewSuccess = View.create(
        viewSuccessName,
        s"The distribution of ${name} success",
        latencySuccess,
        latencyDistribution,
        List(appTagKey).asJava)

    val viewFailureName = Name.create(s"${name}_failure_count")
    val viewFailure = View.create(
        viewFailureName,
        s"The count of ${name} failure",
        countFailure,
        Aggregation.Count.create(),
        List(appTagKey).asJava)

    for {
      _ <- F.delay(viewManager.registerView(viewSuccess)) //it's no op if the view is already registered
      _ <- F.delay(viewManager.registerView(viewFailure))
      start <- timer.clock.monotonic(TimeUnit.MILLISECONDS)
      attemptedResult <- fa.attempt
      end <- timer.clock.monotonic(TimeUnit.MILLISECONDS)
      duration = end - start
      _ <- attemptedResult match {
        case Left(_) =>
          F.delay(statsRecorder.newMeasureMap().put(countFailure, duration).record(tagContext))
        case Right(_) =>
          F.delay(statsRecorder.newMeasureMap().put(latencySuccess, duration).record(tagContext))
      }
      res <- F.fromEither(attemptedResult)
    } yield res
  }

  def gauge[A](name: String, value: Double): F[Unit] = {
    val gauge =  MeasureDouble.create(s"$name", s"Current value of ${name}", "1")
    val view = View.create(
      Name.create(s"${name}_gauge"),
      s"The distribution of ${name} gauge",
      gauge,
      Aggregation.LastValue.create(),
      List(appTagKey).asJava)
    for {
      _ <- F.delay(viewManager.registerView(view))
      _ <- F.delay(statsRecorder.newMeasureMap().put(gauge, value).record(tagContext))
    } yield ()
  }

  def incrementCounter[A](name: String, count: Long = 1): F[Unit] = {
    val counter =  MeasureLong.create(s"$name", s"count of ${name}", "1")
    val view = View.create(
      Name.create(s"${name}_count"),
      s"The count of ${name}",
      counter,
      Aggregation.Count.create(),
      List(appTagKey).asJava)
    for {
      _ <- F.delay(viewManager.registerView(view))
      _ <- F.delay(statsRecorder.newMeasureMap().put(counter, count).record(tagContext))
    } yield ()
  }

  def recordDuration(name: String, duration: Duration)(implicit timer: Timer[F]): F[Unit] = {
    val latency =  MeasureDouble.create(s"$name", s"The latency of ${name} in milliseconds", "ms")

    val latencyDistribution =
      Distribution.create(
        BucketBoundaries.create(
          util.Arrays.asList(
            // Latency in buckets: [>=0ms, >=100ms, >=200ms, >=400ms, >=1s, >=2s, >=4s]
            0.0, 100.0, 200.0, 400.0, 1000.0, 2000.0, 4000.0)))

    val view = View.create(
      Name.create(s"${name}_latency"),
      s"The distribution of ${name} success",
      latency,
      latencyDistribution,
      List(appTagKey).asJava)

    for {
      _ <- F.delay(viewManager.registerView(view))
      _ <- F.delay(statsRecorder.newMeasureMap().put(latency, duration.toMillis).record(tagContext))
    } yield ()
  }
}
