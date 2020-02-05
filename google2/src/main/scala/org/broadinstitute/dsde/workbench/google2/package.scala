package org.broadinstitute.dsde.workbench

import java.util.concurrent.TimeUnit

import cats.implicits._
import cats.effect.{Resource, Sync, Timer}
import cats.mtl.ApplicativeAsk
import com.google.api.core.ApiFutureCallback
import com.google.auth.oauth2.ServiceAccountCredentials
import fs2.{RaiseThrowable, Stream}
import io.chrisdavenport.log4cats.StructuredLogger
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.model.{ErrorReportSource, TraceId}
import io.circe.syntax._

import scala.concurrent.duration._

package object google2 {
  implicit val errorReportSource = ErrorReportSource("google2")

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] = Encoder.encodeString.contramap(_.toString())

  private implicit val loggableGoogleCallEncoder: Encoder[LoggableGoogleCall] = Encoder.forProduct2(
    "response",
    "result"
  )(x => LoggableGoogleCall.unapply(x).get)

  def retryGoogleF[F[_]: Sync: Timer: RaiseThrowable: StructuredLogger, A](retryConfig: RetryConfig)(fa: F[A], traceId: Option[TraceId], action: String): Stream[F, A] = {
    val faWithLogging = for {
      startTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      attempted <- fa.attempt
      endTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      loggingCtx = Map(
        "traceId" -> traceId.map(_.asString).getOrElse(""),
        "googleCall" -> action,
        "duration" -> (endTime - startTime).milliseconds.toString
      )
      _ <- attempted match {
        case Left(e) =>
          val loggableGoogleCall = LoggableGoogleCall(None, "Failed")
          StructuredLogger[F].info(loggingCtx, e)(loggableGoogleCall.asJson.noSpaces) //google library logs error response as well. Revisit if this is too noise
        case Right(r) =>
          val response = if(r == null) "null" else r.toString.take(1024)
          val loggableGoogleCall = LoggableGoogleCall(Some(response), "Succeeded")
          StructuredLogger[F].info(loggingCtx)(loggableGoogleCall.asJson.noSpaces)
      }
      result <- Sync[F].fromEither(attempted)
    } yield result

    Stream.retry[F, A](faWithLogging, retryConfig.retryInitialDelay, retryConfig.retryNextDelay, retryConfig.maxAttempts, retryConfig.retryable)
  }

  def tracedRetryGoogleF[F[_]: Sync: Timer: RaiseThrowable: StructuredLogger, A](retryConfig: RetryConfig)(fa: F[A], action: String)
                                                                      (implicit ev: ApplicativeAsk[F, TraceId]): Stream[F, A] = {
    for {
      traceId <- Stream.eval(ev.ask)
      result <- retryGoogleF(retryConfig)(fa, Some(traceId), action)
    } yield result
  }

  def callBack[A](cb: Either[Throwable, A] => Unit): ApiFutureCallback[A] =
    new ApiFutureCallback[A] {
      @Override def onFailure(t: Throwable): Unit = {
        println(s"failed due to ${t}")
        cb(Left(t))
      }

      @Override def onSuccess(result: A): Unit = cb(Right(result))
    }

  def credentialResource[F[_]: Sync](pathToCredential: String): Resource[F, ServiceAccountCredentials] = for {
    credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential)
    credential <- Resource.liftF(Sync[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
  } yield credential
}

final case class RetryConfig(retryInitialDelay: FiniteDuration, retryNextDelay: FiniteDuration => FiniteDuration, maxAttempts: Int, retryable: Throwable => Boolean = scala.util.control.NonFatal.apply)
final case class LoggableGoogleCall(response: Option[String], result: String)