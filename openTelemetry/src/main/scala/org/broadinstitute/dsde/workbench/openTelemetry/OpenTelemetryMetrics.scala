package org.broadinstitute.dsde.workbench.openTelemetry

import java.nio.file.Path

import cats.ApplicativeError
import cats.effect.{Async, Blocker, ContextShift, Resource, Sync, Timer}
import com.google.auth.oauth2.ServiceAccountCredentials
import fs2.Stream
import io.circe.Decoder
import io.circe.fs2.{byteStreamParser, decoder}
import io.opencensus.exporter.stats.stackdriver.{StackdriverStatsConfiguration, StackdriverStatsExporter}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

trait OpenTelemetryMetrics[F[_]] {
  def time[A](name: String,
              histoBuckets: List[Double],
              tags: Map[String, String] = Map.empty)(fa: F[A])(implicit timer: Timer[F], ae: ApplicativeError[F, Throwable]): F[A]

  def gauge[A](name: String, value: Double, tags: Map[String, String] = Map.empty): F[Unit]

  def incrementCounter[A](name: String, count: Long = 1, tags: Map[String, String] = Map.empty): F[Unit]

  def recordDuration(name: String,
                     duration: FiniteDuration,
                     histoBuckets: List[Double], tags: Map[String, String] = Map.empty)(implicit timer: Timer[F]): F[Unit]
}

object OpenTelemetryMetrics {
  private implicit val googleProjectDecoder: Decoder[GoogleProjectId] = Decoder.forProduct1(
    "project_id"
  )(GoogleProjectId.apply)

  private def parseProject[F[_]: ContextShift: Sync](pathToCredential: Path, blocker: Blocker): Stream[F, GoogleProjectId] =
    fs2.io.file
      .readAll[F](pathToCredential, blocker, 4096)
      .through(byteStreamParser)
      .through(decoder[F, GoogleProjectId])

  def resource[F[_]: ContextShift](pathToCredential: Path,
                                   appName: String,
                                   blocker: Blocker)(implicit F: Async[F]): Resource[F, OpenTelemetryMetricsInterpreter[F]] = for {
    projectId <- Resource.liftF(parseProject[F](pathToCredential, blocker).compile.lastOrError)
    stream <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential.toString)
    credential = ServiceAccountCredentials.fromStream(stream).createScoped(
            Set("https://www.googleapis.com/auth/monitoring",
              "https://www.googleapis.com/auth/cloud-platform").asJava
          )
    configuration = StackdriverStatsConfiguration.builder()
      .setCredentials(credential)
      .setProjectId(projectId.value)
      .build()
    _ <- Resource.make(F.delay(StackdriverStatsExporter.createAndRegister(configuration)))(_ => F.delay(StackdriverStatsExporter.unregister()))
  } yield new OpenTelemetryMetricsInterpreter[F](appName)

  def apply[F[_]](implicit ev: OpenTelemetryMetrics[F]): OpenTelemetryMetrics[F] = ev
}

private[openTelemetry] final case class GoogleProjectId(value: String) extends AnyVal