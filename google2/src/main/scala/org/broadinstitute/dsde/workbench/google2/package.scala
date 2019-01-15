package org.broadinstitute.dsde.workbench

import cats.effect.Timer
import com.google.api.core.ApiFutureCallback
import fs2.{RaiseThrowable, Stream}
import org.broadinstitute.dsde.workbench.model.ErrorReportSource

import scala.concurrent.duration.FiniteDuration

package object google2 {
  implicit val errorReportSource = ErrorReportSource("google")

  def retryGoogleF[F[_]: Timer: RaiseThrowable, A](retryConfig: RetryConfig)(fa: F[A]): Stream[F, A] = Stream.retry[F, A](fa, retryConfig.retryInitialDelay, retryConfig.retryNextDelay, retryConfig.maxAttempts, retryConfig.retryable)

  def callBack[A](cb: Either[Throwable, A] => Unit): ApiFutureCallback[A] =
    new ApiFutureCallback[A] {
      @Override def onFailure(t: Throwable): Unit = cb(Left(t))
      @Override def onSuccess(result: A): Unit = cb(Right(result))
    }
}

final case class RetryConfig(retryInitialDelay: FiniteDuration, retryNextDelay: FiniteDuration => FiniteDuration, maxAttempts: Int, retryable: Throwable => Boolean)
