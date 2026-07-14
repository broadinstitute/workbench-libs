package org.broadinstitute.dsde.workbench

import cats.Show
import cats.implicits._
import cats.effect.{Resource, Sync, Temporal}
import cats.mtl.Ask
import fs2.Stream
import fs2.io.file.Files
import io.circe.{Decoder, Encoder}
import io.circe.fs2.decoder
import org.broadinstitute.dsde.workbench.model.TraceId
import org.typelevel.jawn.AsyncParser
import org.typelevel.log4cats.StructuredLogger
import io.circe.syntax._

import java.io.FileInputStream
import java.nio.file.Path
import scala.concurrent.duration._

package object util2 {
  def addJitter(baseTime: FiniteDuration, maxJitterToAdd: Duration): FiniteDuration =
    baseTime + ((scala.util.Random.nextFloat * maxJitterToAdd.toNanos) nanoseconds)

  def addJitter(baseTime: FiniteDuration): FiniteDuration =
    if (baseTime <= (10 seconds)) {
      addJitter(baseTime, baseTime * 0.1)
    } else {
      addJitter(baseTime, 1 second)
    }

  def readFile[F[_]](path: String)(implicit sf: Sync[F]): Resource[F, FileInputStream] =
    Resource.make(sf.delay(new FileInputStream(path)))(f => sf.delay(f.close()))

  def readPath[F[_]](path: Path)(implicit sf: Sync[F]): Resource[F, FileInputStream] =
    Resource.make(sf.delay(new FileInputStream(path.toString)))(f => sf.delay(f.close()))

  /*
   * Example:
   * scala> org.broadinstitute.dsde.workbench.util.readJsonFileToA[IO, List[String]](java.nio.file.Paths.get("/tmp/list"), None).compile.lastOrError.unsafeRunSync
   * res0: List[String] = List(this is great)
   *
   */
  def readJsonFileToA[F[_]: Sync: Files, A: Decoder](path: Path): Stream[F, A] =
    Files[F]
      .readAll(path, 4096)
      .through(fs2.text.utf8Decode)
      .through(_root_.io.circe.fs2.stringParser(AsyncParser.SingleValue))
      .through(decoder)

  implicit private val loggableGoogleCallEncoder: Encoder[LoggableCloudCall] = Encoder.forProduct2(
    "response",
    "result"
  )(x => LoggableCloudCall.unapply(x).get)

  // util2 intentionally has no dependency on io.kubernetes:client-java. Some callers (e.g. workbench-google2,
  // workbench-azure) route Kubernetes API calls through withLogging; on failure those throw
  // io.kubernetes.client.openapi.ApiException, whose getResponseBody carries the useful server-side error
  // detail. Rather than referencing that class directly (which would drag client-java onto every consumer's
  // classpath), we recognize it by class name and read getResponseBody reflectively. When client-java is not
  // on the classpath the name check simply never matches and we fall back to getMessage — no reference to the
  // class is ever loaded, so there is no NoClassDefFoundError risk.
  private val KubernetesApiExceptionClassName = "io.kubernetes.client.openapi.ApiException"

  private def isKubernetesApiException(t: Throwable): Boolean = {
    @scala.annotation.tailrec
    def loop(clazz: Class[_]): Boolean =
      clazz != null && (clazz.getName == KubernetesApiExceptionClassName || loop(clazz.getSuperclass))
    loop(t.getClass)
  }

  // The response body logged on failure: for a Kubernetes ApiException that is getResponseBody (read
  // reflectively), otherwise the exception message. Falls back to getMessage if the reflective call fails.
  private def failureResponse(t: Throwable): Option[String] =
    if (isKubernetesApiException(t))
      scala.util
        .Try(Some(t.getClass.getMethod("getResponseBody").invoke(t).asInstanceOf[String]))
        .getOrElse(Some(t.getMessage))
    else
      Some(t.getMessage)

  def withLogging[F[_]: Temporal, A](fa: F[A],
                                     traceId: Option[TraceId],
                                     action: String,
                                     resultFormatter: Show[A] =
                                       Show.show[A](a => if (a == null) "null" else a.toString.take(1024)),
                                     warnOnError: Boolean = false,
                                     actionName: String = "googleCall"
  )(implicit
    logger: StructuredLogger[F]
  ): F[A] =
    for {
      res <- Temporal[F].timed(fa.attempt)
      loggingCtx = Map(
        "traceId" -> traceId.map(_.asString).getOrElse(""),
        actionName -> action,
        "duration" -> res._1.toMillis.toString
      )
      _ <- res._2 match {
        case Left(e) =>
          val loggableCloudCall = LoggableCloudCall(failureResponse(e), "Failed")
          val ctx = loggingCtx ++ Map("result" -> "Failed")
          if (warnOnError) {
            logger.warn(ctx, e)(loggableCloudCall.asJson.noSpaces)
          } else {
            logger.error(ctx, e)(loggableCloudCall.asJson.noSpaces)
          }
        case Right(r) =>
          val response = Option(resultFormatter.show(r))
          val loggableCloudCall = LoggableCloudCall(response, "Succeeded")
          val ctx = loggingCtx ++ Map("result" -> "Succeeded")
          logger.info(ctx)(loggableCloudCall.asJson.noSpaces)
      }
      result <- Temporal[F].fromEither(res._2)
    } yield result

  def tracedLogging[F[_]: Temporal, A](fa: F[A],
                                       action: String,
                                       resultFormatter: Show[A] =
                                         Show.show[A](a => if (a == null) "null" else a.toString.take(1024))
  )(implicit
    logger: StructuredLogger[F],
    ev: Ask[F, TraceId]
  ): F[A] =
    for {
      traceId <- ev.ask
      result <- withLogging(fa, Some(traceId), action, resultFormatter)
    } yield result
}
