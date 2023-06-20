package org.broadinstitute.dsde.workbench.util

import akka.actor.ActorSystem
import akka.pattern._
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
 * Created by tsharpe on 9/21/15.
 */
trait Retry {
  this: LazyLogging =>
  val system: ActorSystem

  type Predicate[A] = A => Boolean

  /**
   * A Future that has potentially been retried, with accumulated errors.
   * There are 3 cases:
   * 1. The future failed 1 or more times, and the final result is an error.
   *   - This is represented as {{{Left(NonEmptyList(errors))}}}
   * 2. The future failed 1 or more times, but eventually succeeded.
   *   - This is represented as {{{Right(List(errors), A)}}}
   * 3. The future succeeded the first time.
   *   - This is represented as {{{Right(List.empty, A)}}}
   */
  type RetryableFuture[A] = Future[Either[NonEmptyList[Throwable], (List[Throwable], A)]]

  def always[A]: Predicate[A] = _ => true

  val defaultErrorMessage = "retry-able operation failed"

  def retry[T](pred: Predicate[Throwable] = always, failureLogMessage: String = defaultErrorMessage)(
    op: () => Future[T]
  )(implicit executionContext: ExecutionContext): RetryableFuture[T] =
    retryInternal(allBackoffIntervals, pred, failureLogMessage)(op)

  def retryExponentially[T](pred: Predicate[Throwable] = always, failureLogMessage: String = defaultErrorMessage)(
    op: () => Future[T]
  )(implicit executionContext: ExecutionContext): RetryableFuture[T] =
    retryInternal(exponentialBackOffIntervals, pred, failureLogMessage)(op)

  /**
   * will retry at the given interval until success or the overall timeout has passed
   * @param pred which failures to retry
   * @param interval how often to retry
   * @param timeout how long from now to give up
   * @param op what to try
   * @param executionContext
   * @tparam T
   * @return
   */
  def retryUntilSuccessOrTimeout[T](pred: Predicate[Throwable] = always,
                                    failureLogMessage: String = defaultErrorMessage
  )(
    interval: FiniteDuration,
    timeout: FiniteDuration
  )(op: () => Future[T])(implicit executionContext: ExecutionContext): RetryableFuture[T] = {
    val trialCount = Math.ceil(timeout / interval).toInt
    retryInternal(Seq.fill(trialCount)(interval), pred, failureLogMessage)(op)
  }

  private def retryInternal[T](
    backoffIntervals: Seq[FiniteDuration],
    pred: Predicate[Throwable],
    failureLogMessage: String
  )(op: () => Future[T])(implicit executionContext: ExecutionContext): RetryableFuture[T] = {

    def loop(remainingBackoffIntervals: Seq[FiniteDuration], errors: => List[Throwable]): RetryableFuture[T] =
      op().map(x => Right((errors, x))).recoverWith {
        case t if pred(t) && !remainingBackoffIntervals.isEmpty =>
          logger.info(
            s"$failureLogMessage: ${remainingBackoffIntervals.size} retries remaining, retrying in ${remainingBackoffIntervals.head}",
            t
          )
          after(remainingBackoffIntervals.head, system.scheduler) {
            loop(remainingBackoffIntervals.tail, t :: errors)
          }

        case t =>
          if (remainingBackoffIntervals.isEmpty) {
            logger.info(s"$failureLogMessage: no retries remaining", t)
          } else {
            logger.info(s"$failureLogMessage: retries remain but predicate failed, not retrying", t)
          }

          Future.successful(Left(NonEmptyList(t, errors)))
      }

    loop(backoffIntervals, List.empty)
  }

  private val allBackoffIntervals = Seq(100 milliseconds, 1 second, 3 seconds, 60 seconds)

  // starting value in milliseconds, multiply by multiples of this each step, how many steps/elements
  protected def createExponentialBackOffIntervals(startingValue: Int,
                                                  multiplyBy: Int,
                                                  elements: Int,
                                                  withJitter: Boolean = true,
                                                  jitterValue: Int = 1000
  ): Seq[FiniteDuration] = {
    val intervals = Seq.iterate(startingValue, elements)(_ * multiplyBy).map(_ milliseconds)
    if (withJitter) intervals.map(i => addJitter(i, jitterValue milliseconds)) else intervals
  }

  // 1000, 2000, ...., 64000 milliseconds
  protected def exponentialBackOffIntervals: Seq[FiniteDuration] =
    createExponentialBackOffIntervals(1000, 2, 7)

  /**
   * Converts an RetryableFuture[A] to a Future[A].
   */
  implicit protected[util] def retryableFutureToFuture[A](
    af: RetryableFuture[A]
  )(implicit executionContext: ExecutionContext): Future[A] =
    af.flatMap {
      // take the head (most recent) error
      case Left(NonEmptyList(t, _)) => Future.failed(t)
      // return the successful result, throw out any errors
      case Right((_, a)) => Future.successful(a)
    }

}
