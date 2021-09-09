package org.broadinstitute.dsde.workbench.openTelemetry

import cats.ApplicativeError
import cats.effect.{Async, Temporal}
import cats.syntax.all._
import io.opencensus.stats.Aggregation.Distribution
import io.opencensus.stats.Measure.{MeasureDouble, MeasureLong}
import io.opencensus.stats.View.Name
import io.opencensus.stats.{Aggregation, BucketBoundaries, Stats, View}
import io.opencensus.tags.{TagContext, TagKey, TagValue, Tags}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

class OpenTelemetryMetricsInterpreter[F[_]](appName: String)(implicit F: Async[F]) extends OpenTelemetryMetrics[F] {
  private val viewManager = Stats.getViewManager
  private val statsRecorder = Stats.getStatsRecorder
  private val appTagKey = TagKey.create("app")

  private val appTagValue = TagValue.create(appName)

  private val tagger = Tags.getTagger()
  val tagContext = tagger
    .emptyBuilder()
    .putLocal(appTagKey, appTagValue)
    .build()

  // Aggregation doc: https://opencensus.io/stats/view/#aggregations
  def time[A](name: String, distributionBucket: List[FiniteDuration], tags: Map[String, String] = Map.empty)(
    fa: F[A]
  )(implicit temporal: Temporal[F], ae: ApplicativeError[F, Throwable]): F[A] = {
    val latencySuccess =
      MeasureDouble.create(s"${name}_success_latency", "The successful io latency in milliseconds", "ms")
    val countFailure = MeasureLong.create(s"${name}_failure_count", s"count of $name", "1")

    val latencyDistribution =
      Distribution.create(BucketBoundaries.create(distributionBucket.map(x => Double.box(x.toMillis)).asJava))

    val tagKvs = tags.map { case (k, v) => (TagKey.create(k), TagValue.create(v)) }
    val tc = getTagContext(tagKvs)

    val viewSuccessName = Name.create(s"$appName/${name}_success_latency")
    val viewSuccess = View.create(viewSuccessName,
                                  s"The distribution of $name success",
                                  latencySuccess,
                                  latencyDistribution,
                                  (List(appTagKey) ++ tagKvs.keys).asJava
    )

    val viewFailureName = Name.create(s"$appName/${name}_failure_count")
    val viewFailure = View.create(viewFailureName,
                                  s"The count of $name failure",
                                  countFailure,
                                  Aggregation.Count.create(),
                                  (List(appTagKey) ++ tagKvs.keys).asJava
    )

    for {
      _ <- F.delay(viewManager.registerView(viewSuccess)) //it's no op if the view is already registered
      _ <- F.delay(viewManager.registerView(viewFailure))
      res <- temporal.timed(fa.attempt)
      (duration, attemptedResult) = res
      _ <- attemptedResult match {
        case Left(_) =>
          F.delay(statsRecorder.newMeasureMap().put(countFailure, duration.toMillis).record(tc))
        case Right(_) =>
          F.delay(statsRecorder.newMeasureMap().put(latencySuccess, duration.toMillis).record(tc))
      }
      res <- F.fromEither(attemptedResult)
    } yield res
  }

  def gauge[A](name: String, value: Double, tags: Map[String, String] = Map.empty): F[Unit] = {
    val gauge = MeasureDouble.create(s"${name}_gauge", s"Current value of $name", "1")
    val tagKvs = tags.map { case (k, v) => (TagKey.create(k), TagValue.create(v)) }
    val tc = getTagContext(tagKvs)
    val view = View.create(
      Name.create(s"$appName/${name}_gauge"),
      s"The distribution of $name gauge",
      gauge,
      Aggregation.LastValue.create(),
      (List(appTagKey) ++ tagKvs.keys).asJava
    )
    for {
      _ <- F.delay(viewManager.registerView(view))
      _ <- F.delay(statsRecorder.newMeasureMap().put(gauge, value).record(tc))
    } yield ()
  }

  def incrementCounter[A](name: String, count: Long = 1, tags: Map[String, String] = Map.empty): F[Unit] = {
    val counter = MeasureLong.create(s"${name}_count", s"count of $name", "1")
    val tagKvs = tags.map { case (k, v) => (TagKey.create(k), TagValue.create(v)) }
    val tc = getTagContext(tagKvs)
    val view = View.create(Name.create(s"$appName/${name}_count"),
                           s"The count of $name",
                           counter,
                           Aggregation.Sum.create(),
                           (List(appTagKey) ++ tagKvs.keys).asJava
    )
    for {
      _ <- F.delay(viewManager.registerView(view))
      _ <- F.delay(statsRecorder.newMeasureMap().put(counter, count).record(tc))
    } yield ()
  }

  def recordDuration(name: String,
                     duration: FiniteDuration,
                     distributionBucket: List[FiniteDuration],
                     tags: Map[String, String] = Map.empty
  )(implicit temporal: Temporal[F]): F[Unit] = {
    val latency = MeasureDouble.create(s"${name}_duration", s"The latency of $name in milliseconds", "ms")

    val latencyDistribution =
      Distribution.create(BucketBoundaries.create(distributionBucket.map(x => Double.box(x.toMillis)).asJava))

    val tagKvs = tags.map { case (k, v) => (TagKey.create(k), TagValue.create(v)) }
    val tc = getTagContext(tagKvs)
    val view = View.create(Name.create(s"$appName/${name}_duration"),
                           s"The distribution of $name duration",
                           latency,
                           latencyDistribution,
                           (List(appTagKey) ++ tagKvs.keys).asJava
    )

    for {
      _ <- F.delay(viewManager.registerView(view))
      _ <- F.delay(statsRecorder.newMeasureMap().put(latency, duration.toMillis).record(tc))
    } yield ()
  }

  private def getTagContext(tags: Map[TagKey, TagValue]): TagContext =
    if (tags.isEmpty) tagContext
    else {
      val builder = tagger
        .emptyBuilder()
        .putLocal(appTagKey, appTagValue)

      tags.foreach { case (k, v) =>
        builder.putLocal(k, v)
      }

      builder.build()
    }
}
