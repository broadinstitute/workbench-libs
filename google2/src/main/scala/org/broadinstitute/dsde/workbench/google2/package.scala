package org.broadinstitute.dsde.workbench

import java.util.concurrent.TimeUnit

import DoneCheckableSyntax._
import cats.Show
import cats.syntax.all._
import cats.effect.{Resource, Sync, Timer}
import cats.mtl.Ask
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.core.ApiFutureCallback
import com.google.api.gax.core.BackgroundResource
import com.google.api.services.container.ContainerScopes
import com.google.auth.oauth2.{ServiceAccountCredentials, UserCredentials}
import com.google.cloud.billing.v1.ProjectBillingInfo
import com.google.cloud.compute.v1.Operation
import com.google.cloud.resourcemanager.Project
import fs2.{RaiseThrowable, Stream}
import io.chrisdavenport.log4cats.StructuredLogger
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.model.{ErrorReportSource, TraceId, WorkbenchException}
import io.circe.syntax._

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

package object google2 {
  implicit val errorReportSource = ErrorReportSource("google2")

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] = Encoder.encodeString.contramap(_.toString())

  implicit private val loggableGoogleCallEncoder: Encoder[LoggableGoogleCall] = Encoder.forProduct2(
    "response",
    "result"
  )(x => LoggableGoogleCall.unapply(x).get)

  def retryGoogleF[F[_]: Sync: Timer: RaiseThrowable, A](
    retryConfig: RetryConfig
  )(fa: F[A],
    traceId: Option[TraceId],
    action: String,
    resultFormatter: Show[A] = Show.show[A](a => if (a == null) "null" else a.toString.take(1024))
  )(implicit logger: StructuredLogger[F]): Stream[F, A] = {
    val faWithLogging = withLogging(fa, traceId, action, resultFormatter)

    Stream.retry[F, A](faWithLogging,
                       retryConfig.retryInitialDelay,
                       retryConfig.retryNextDelay,
                       retryConfig.maxAttempts,
                       retryConfig.retryable
    )
  }

  def withLogging[F[_]: Sync: Timer, A](fa: F[A],
                                        traceId: Option[TraceId],
                                        action: String,
                                        resultFormatter: Show[A] =
                                          Show.show[A](a => if (a == null) "null" else a.toString.take(1024))
  )(implicit
    logger: StructuredLogger[F]
  ): F[A] =
    for {
      startTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      attempted <- fa.attempt
      endTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      loggingCtx = Map(
        "traceId" -> traceId.map(_.asString).getOrElse(""),
        "googleCall" -> action,
        "duration" -> (endTime - startTime).milliseconds.toString
      )
      _ <- attempted match {
        case Left(e: io.kubernetes.client.openapi.ApiException) =>
          val loggableGoogleCall = LoggableGoogleCall(Some(e.getResponseBody), "Failed")
          val ctx = loggingCtx ++ Map("result" -> "Failed")
          logger.error(ctx, e)(loggableGoogleCall.asJson.noSpaces)
        case Left(e) =>
          val loggableGoogleCall = LoggableGoogleCall(None, "Failed")
          val ctx = loggingCtx ++ Map("result" -> "Failed")
          logger.error(ctx, e)(loggableGoogleCall.asJson.noSpaces)
        case Right(r) =>
          val response = Option(resultFormatter.show(r))
          val loggableGoogleCall = LoggableGoogleCall(response, "Succeeded")
          val ctx = loggingCtx ++ Map("result" -> "Succeeded")
          logger.info(ctx)(loggableGoogleCall.asJson.noSpaces)
      }
      result <- Sync[F].fromEither(attempted)
    } yield result

  def tracedLogging[F[_]: Timer: Sync, A](fa: F[A],
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

  def tracedRetryGoogleF[F[_]: Sync: Timer: RaiseThrowable: StructuredLogger, A](
    retryConfig: RetryConfig
  )(fa: F[A],
    action: String,
    resultFormatter: Show[A] = Show.show[A](a => if (a == null) "null" else a.toString.take(1024))
  )(implicit ev: Ask[F, TraceId]): Stream[F, A] =
    for {
      traceId <- Stream.eval(ev.ask)
      result <- retryGoogleF(retryConfig)(fa, Some(traceId), action, resultFormatter)
    } yield result

  def callBack[A](cb: Either[Throwable, A] => Unit): ApiFutureCallback[A] =
    new ApiFutureCallback[A] {
      @Override def onFailure(t: Throwable): Unit = cb(Left(t))

      @Override def onSuccess(result: A): Unit = cb(Right(result))
    }

  def credentialResource[F[_]: Sync](pathToCredential: String): Resource[F, ServiceAccountCredentials] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential)
      credential <- Resource.eval(Sync[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
    } yield credential

  def userCredentials[F[_]: Sync](pathToCredential: String): Resource[F, UserCredentials] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential)
      credential <- Resource.eval(Sync[F].delay(UserCredentials.fromStream(credentialFile)))
    } yield credential

  // returns legacy GoogleCredential object which is only used for the legacy com.google.api.services client
  def legacyGoogleCredential[F[_]: Sync](pathToCredential: String): Resource[F, GoogleCredential] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential)
      credential <- Resource.eval(
        Sync[F].delay(GoogleCredential.fromStream(credentialFile).createScoped(ContainerScopes.all()))
      )
    } yield credential

  def backgroundResourceF[F[_]: Sync, A <: BackgroundResource](resource: => A): Resource[F, A] =
    Resource.make(Sync[F].delay(resource))(c => Sync[F].delay(c.shutdown()) >> Sync[F].delay(c.close()))

  def autoClosableResourceF[F[_]: Sync, A <: AutoCloseable](resource: => A): Resource[F, A] =
    Resource.make(Sync[F].delay(resource))(c => Sync[F].delay(c.close()))

  // Recovers a F[A] to an F[Option[A]] depending on predicate
  def recoverF[F[_]: Sync, A](fa: F[A], pred: Throwable => Boolean): F[Option[A]] =
    fa.map(Option(_)).recover { case e if pred(e) => None }

  // Note: This method may reach maxAttempts without hitting the Done condition.
  // If you need to check whether the Done condition was met, you may want to use the
  // method streamUntilDoneOrTimeout() instead.
  def streamFUntilDone[F[_]: Timer, A: DoneCheckable](fa: F[A], maxAttempts: Int, delay: FiniteDuration): Stream[F, A] =
    (Stream.sleep_(delay) ++ Stream.eval(fa))
      .repeatN(maxAttempts)
      .takeThrough(!_.isDone)

  // Distinctly from the method streamFUntilDone(), this method raises a StreamTimeoutError if the Done condition is not met
  // by the time maxAttempts are exhausted. Therefore callers may want to use this method instead of streamFUntilDone()
  // if they want to ascertain the Done condition was satisfied and take action otherwise.
  // See org.broadinstitute.dsde.workbench.google2.GoogleDataprocInterpreter.stopCluster() for examples.
  def streamUntilDoneOrTimeout[F[_]: Sync: Timer, A: DoneCheckable](fa: F[A],
                                                                    maxAttempts: Int,
                                                                    delay: FiniteDuration,
                                                                    timeoutErrorMessage: String
  ): F[A] =
    streamFUntilDone(fa, maxAttempts, delay).last
      .evalMap {
        case Some(a) if a.isDone => Sync[F].pure(a)
        case _                   => Sync[F].raiseError[A](StreamTimeoutError(timeoutErrorMessage))
      }
      .compile
      .lastOrError

  val showOperation: Show[Operation] = Show.show[Operation](op =>
    if (op == null)
      "null"
    else
      s"operationType=${op.getOperationType}, progress=${op.getProgress}, status=${op.getStatus}, startTime=${op.getStartTime}"
  )

  val showBillingInfo: Show[Option[ProjectBillingInfo]] =
    Show.show[Option[ProjectBillingInfo]](info => s"isBillingEnabled: ${info.map(_.getBillingEnabled)}")

  implicit val showProject: Show[Option[Project]] =
    Show.show[Option[Project]](project => s"project name: ${project.map(_.getName)}")
}

final case class StreamTimeoutError(override val getMessage: String) extends WorkbenchException

final case class RetryConfig(retryInitialDelay: FiniteDuration,
                             retryNextDelay: FiniteDuration => FiniteDuration,
                             maxAttempts: Int,
                             retryable: Throwable => Boolean = scala.util.control.NonFatal.apply
)
final case class LoggableGoogleCall(response: Option[String], result: String)

trait DoneCheckable[A] {
  def isDone(a: A): Boolean
}

object DoneCheckableInstances {
  implicit val containerDoneCheckable = new DoneCheckable[com.google.container.v1.Operation] {
    def isDone(op: com.google.container.v1.Operation): Boolean =
      op.getStatus == com.google.container.v1.Operation.Status.DONE
  }
  implicit val computeDoneCheckable = new DoneCheckable[com.google.cloud.compute.v1.Operation] {
    def isDone(op: com.google.cloud.compute.v1.Operation): Boolean = op.getStatus == "DONE"
  }
}

final case class DoneCheckableOps[A](a: A)(implicit ev: DoneCheckable[A]) {
  def isDone: Boolean = ev.isDone(a)
}

object DoneCheckableSyntax {
  implicit def doneCheckableSyntax[A](a: A)(implicit ev: DoneCheckable[A]): DoneCheckableOps[A] = DoneCheckableOps(a)
}
