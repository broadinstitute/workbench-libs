package org.broadinstitute.dsde.workbench.openTelemetry

import cats.ApplicativeError
import cats.effect.kernel.Temporal
import cats.effect.{Async, Resource, Sync}
import com.google.auth.oauth2.ServiceAccountCredentials
import fs2.Stream
import fs2.io.file.Files
import io.circe.Decoder
import io.circe.fs2.{byteStreamParser, decoder}
import io.opencensus.exporter.stats.prometheus.PrometheusStatsCollector
import io.opencensus.exporter.trace.stackdriver.{StackdriverTraceConfiguration, StackdriverTraceExporter}
import io.prometheus.client.exporter.HTTPServer

import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.FiniteDuration

trait OpenTelemetryMetrics[F[_]] {
  def time[A](name: String, distributionBucket: List[FiniteDuration], tags: Map[String, String] = Map.empty)(
    fa: F[A]
  )(implicit temporal: Temporal[F], ae: ApplicativeError[F, Throwable]): F[A]

  def gauge[A](name: String, value: Double, tags: Map[String, String] = Map.empty): F[Unit]

  def incrementCounter[A](name: String, count: Long = 1, tags: Map[String, String] = Map.empty): F[Unit]

  def recordDuration(name: String,
                     duration: FiniteDuration,
                     distributionBucket: List[FiniteDuration],
                     tags: Map[String, String] = Map.empty
  )(implicit temporal: Temporal[F]): F[Unit]
}

object OpenTelemetryMetrics {
  implicit private val googleProjectDecoder: Decoder[GoogleProjectId] = Decoder.forProduct1(
    "project_id"
  )(GoogleProjectId.apply)

  private def parseProject[F[_]: Files: Sync](pathToCredential: Path): Stream[F, GoogleProjectId] =
    fs2.io.file
      .Files[F]
      .readAll(pathToCredential, 4096)
      .through(byteStreamParser)
      .through(decoder[F, GoogleProjectId])

  def resource[F[_]](appName: String, endpointPort: Int = 9098)(implicit
    F: Async[F]
  ): Resource[F, OpenTelemetryMetricsInterpreter[F]] =
    for {
      _ <- Resource.eval(F.delay(PrometheusStatsCollector.createAndRegister())) // Cannot unregister Prometheus
      _ <- Resource.make(F.delay(new HTTPServer(endpointPort)))(server => F.delay(server.close()))
    } yield new OpenTelemetryMetricsInterpreter[F](appName)

  def registerTracing[F[_]](pathToCredential: Path)(implicit
    F: Async[F]
  ): Resource[F, Unit] =
    for {
      projectId <- Resource.eval(parseProject[F](pathToCredential).compile.lastOrError)
      stream <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential.toString)
      credential = ServiceAccountCredentials
        .fromStream(stream)
        .createScoped(
          Set("https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/trace.append").asJava
        )
      configuration = StackdriverTraceConfiguration
        .builder()
        .setCredentials(credential)
        .setProjectId(projectId.value)
        .build()
      _ <- Resource.make(F.delay(StackdriverTraceExporter.createAndRegister(configuration)))(_ =>
        F.unit // Seems if we unregister here, no tracing will be exported
      )
    } yield ()

  def apply[F[_]](implicit ev: OpenTelemetryMetrics[F]): OpenTelemetryMetrics[F] = ev
}

final private[openTelemetry] case class GoogleProjectId(value: String) extends AnyVal
