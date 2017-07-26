package org.broadinstitute.dsde.workbench.util

import java.util.concurrent.TimeoutException

import akka.actor.ActorContext
import akka.pattern.after

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Created by dvoet on 10/5/15.
 */
trait FutureSupport {
  /**
   * Converts a Future[T] to a Future[Try[T]]. Even if the operation of the Future failed, the resulting
   * Future is considered a success and the error is available in the Try.
   *
   * @param f
   * @tparam T
   * @return
   */
  def toFutureTry[T](f: Future[T])(implicit executionContext: ExecutionContext): Future[Try[T]] = f map(Success(_)) recover { case t => Failure(t) }

  /**
   * Returns a failed future if any of the input tries have failed, otherwise returns the input with tries unwrapped in a successful Future
   */
  def assertSuccessfulTries[K, T](tries: Map[K, Try[T]])(implicit executionContext: ExecutionContext): Future[Map[K, T]] = {
    val failures = tries.values.collect{ case Failure(t) => t }
    if (failures.isEmpty) Future.successful(tries.map { case (k, v) => k -> v.get}) else Future.failed(failures.head)
  }

  /**
    * Adds non-blocking timeout support to futures.
    * Example usage:
    * {{{
    *   val future = Future(Thread.sleep(1000*60*60*24*365)) // 1 year
    *   Await.result(future.withTimeout(5 seconds, "Timed out"), 365 days)
    *   // returns in 5 seconds
    * }}}
    */
  implicit class FutureWithTimeout[A](f: Future[A]) {
    def withTimeout(duration: FiniteDuration, errMsg: String)(implicit context: ActorContext, ec: ExecutionContext): Future[A] =
      Future.firstCompletedOf(Seq(f, after(duration, context.system.scheduler)(Future.failed(new TimeoutException(errMsg)))))
  }
}

object FutureSupport extends FutureSupport
