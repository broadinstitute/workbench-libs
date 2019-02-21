package org.broadinstitute.dsde.workbench

import cats.effect.{Resource, Sync, Timer}
import com.google.api.core.ApiFutureCallback
import com.google.auth.oauth2.ServiceAccountCredentials
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

  def credentialResource[F[_]: Sync](pathToCredential: String): Resource[F, ServiceAccountCredentials] = for {
    credentialFile <- org.broadinstitute.dsde.workbench.util.readFile(pathToCredential)
    credential <- Resource.liftF(Sync[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
  } yield credential
}

final case class RetryConfig(retryInitialDelay: FiniteDuration, retryNextDelay: FiniteDuration => FiniteDuration, maxAttempts: Int, retryable: Throwable => Boolean)
