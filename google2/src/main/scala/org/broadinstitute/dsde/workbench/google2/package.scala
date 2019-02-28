package org.broadinstitute.dsde.workbench

import java.util.concurrent.TimeUnit

import cats.implicits._
import cats.effect.{Resource, Sync, Timer}
import com.google.api.core.ApiFutureCallback
import com.google.auth.oauth2.ServiceAccountCredentials
import fs2.{RaiseThrowable, Stream}
import io.chrisdavenport.log4cats.Logger
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.model.{ErrorReportSource, TraceId}
import org.broadinstitute.dsde.workbench.google2.JsonCodec._
import io.circe.syntax._

import scala.concurrent.duration._

package object google2 {
  implicit val errorReportSource = ErrorReportSource("google")

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] = Encoder.encodeString.contramap(_.toString())

  private implicit val loggableGoogleCallEncoder: Encoder[LoggableGoogleCall] = Encoder.forProduct5(
    "traceId",
    "googleCall",
    "duration",
    "response",
    "result"
  )(x => LoggableGoogleCall.unapply(x).get)

  def retryGoogleF[F[_]: Sync: Timer: RaiseThrowable: Logger, A](retryConfig: RetryConfig)(fa: F[A], traceId: Option[TraceId], action: String): Stream[F, A] = {
    val faWithLogging = for {
      startTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      attempted <- fa.attempt
      endTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      _ <- attempted match {
        case Left(e) =>
          val loggableGoogleCall = LoggableGoogleCall(traceId, action, (endTime - startTime).milliseconds, None, "Failed")
          Logger[F].error(e)(loggableGoogleCall.asJson.noSpaces) //google library logs error response as well. Revisit if this is too noise
        case Right(r) =>
          val response = if(r == null) "null" else r.toString.take(1024)
          val loggableGoogleCall = LoggableGoogleCall(traceId, action, (endTime - startTime).milliseconds, Some(response), "Succeeded")
          Logger[F].info(loggableGoogleCall.asJson.noSpaces)
      }
      result <- Sync[F].fromEither(attempted)
    } yield result

    Stream.retry[F, A](faWithLogging, retryConfig.retryInitialDelay, retryConfig.retryNextDelay, retryConfig.maxAttempts, retryConfig.retryable)
  }

  def callBack[A](cb: Either[Throwable, A] => Unit): ApiFutureCallback[A] =
    new ApiFutureCallback[A] {
      @Override def onFailure(t: Throwable): Unit = cb(Left(t))
      @Override def onSuccess(result: A): Unit = cb(Right(result))
    }

  def credentialResource[F[_]: Sync](pathToCredential: String): Resource[F, ServiceAccountCredentials] = for {
    credentialFile <- org.broadinstitute.dsde.workbench.util.readFile(pathToCredential)
    credential <- Resource.liftF(Sync[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
  } yield credential
}

final case class RetryConfig(retryInitialDelay: FiniteDuration, retryNextDelay: FiniteDuration => FiniteDuration, maxAttempts: Int, retryable: Throwable => Boolean = scala.util.control.NonFatal.apply)
final case class LoggableGoogleCall(traceId: Option[TraceId], googleCall: String, duration: FiniteDuration, response: Option[String], result: String)