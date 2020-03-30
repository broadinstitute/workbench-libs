package org.broadinstitute.dsde.workbench

import java.util.concurrent.TimeUnit

import DoneCheckableSyntax._
import cats.implicits._
import cats.effect.{Resource, Sync, Timer}
import cats.mtl.ApplicativeAsk
import com.google.api.core.ApiFutureCallback
import com.google.api.gax.core.BackgroundResource
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

  implicit private val loggableGoogleCallEncoder: Encoder[LoggableGoogleCall] = Encoder.forProduct2(
    "response",
    "result"
  )(x => LoggableGoogleCall.unapply(x).get)

  def retryGoogleF[F[_]: Sync: Timer: RaiseThrowable, A](
    retryConfig: RetryConfig
  )(fa: F[A], traceId: Option[TraceId], action: String)(implicit logger: StructuredLogger[F]): Stream[F, A] = {
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

          val msg = loggingCtx.asJson.deepMerge(loggableGoogleCall.asJson)
          // Duplicate MDC context in regular logging until log formats can be changed in apps
          logger.error(loggingCtx, e)(msg.noSpaces)
        case Right(r) =>
          val response = if (r == null) "null" else r.toString.take(1024)
          val loggableGoogleCall = LoggableGoogleCall(Some(response), "Succeeded")
          val msg = loggingCtx.asJson.deepMerge(loggableGoogleCall.asJson)
          logger.info(loggingCtx)(msg.noSpaces)
      }
      result <- Sync[F].fromEither(attempted)
    } yield result

    Stream.retry[F, A](faWithLogging,
                       retryConfig.retryInitialDelay,
                       retryConfig.retryNextDelay,
                       retryConfig.maxAttempts,
                       retryConfig.retryable)
  }

  def tracedRetryGoogleF[F[_]: Sync: Timer: RaiseThrowable: StructuredLogger, A](
    retryConfig: RetryConfig
  )(fa: F[A], action: String)(implicit ev: ApplicativeAsk[F, TraceId]): Stream[F, A] =
    for {
      traceId <- Stream.eval(ev.ask)
      result <- retryGoogleF(retryConfig)(fa, Some(traceId), action)
    } yield result

  def callBack[A](cb: Either[Throwable, A] => Unit): ApiFutureCallback[A] =
    new ApiFutureCallback[A] {
      @Override def onFailure(t: Throwable): Unit = cb(Left(t))

      @Override def onSuccess(result: A): Unit = cb(Right(result))
    }

  def credentialResource[F[_]: Sync](pathToCredential: String): Resource[F, ServiceAccountCredentials] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential)
      credential <- Resource.liftF(Sync[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
    } yield credential

  def backgroundResourceF[F[_]: Sync, A <: BackgroundResource](resource: => A): Resource[F, A] =
    Resource.make(Sync[F].delay(resource))(c => Sync[F].delay(c.shutdown()) >> Sync[F].delay(c.close()))

  def autoClosableResourceF[F[_]: Sync, A <: AutoCloseable](resource: => A): Resource[F, A] =
    Resource.make(Sync[F].delay(resource))(c => Sync[F].delay(c.close()))

  // Recovers a F[A] to an F[Option[A]] depending on predicate
  def recoverF[F[_]: Sync, A](fa: F[A], pred: Throwable => Boolean): F[Option[A]] =
    fa.map(Option(_)).recover { case e if pred(e) => None }
  \
            def streamFUntilDone[F[_]: Timer, A: DoneCheckable](fa: F[A],
                                                             maxAttempts: Int,
                                                             delay: FiniteDuration): Stream[F, A] =
      (Stream.eval(fa) ++ Stream.sleep_(delay))
        .repeatN(maxAttempts)
        .takeThrough(!_.isDone)
}

final case class RetryConfig(retryInitialDelay: FiniteDuration,
                             retryNextDelay: FiniteDuration => FiniteDuration,
                             maxAttempts: Int,
                             retryable: Throwable => Boolean = scala.util.control.NonFatal.apply)
final case class LoggableGoogleCall(response: Option[String], result: String)

trait DoneCheckable[A] {
  def isDone(a: A): Boolean
}

object DoneCheckableInstances {
  implicit val containerDoneCheckable = new DoneCheckable[com.google.container.v1.Operation]{
    def isDone(op: com.google.container.v1.Operation): Boolean = op.getStatus == "DONE"
  }
  implicit val computeDoneCheckable = new DoneCheckable[com.google.cloud.compute.v1.Operation]{
    def isDone(op: com.google.cloud.compute.v1.Operation): Boolean = op.getStatus == "DONE"
  }
}

final case class DoneCheckableOps[A](a: A)(implicit ev: DoneCheckable[A]) {
  def isDone: Boolean = ev.isDone(a)
}

object DoneCheckableSyntax {
  implicit def doneCheckableSyntax[A](a: A)(implicit ev: DoneCheckable[A]): DoneCheckableOps[A] = DoneCheckableOps(a)
}
