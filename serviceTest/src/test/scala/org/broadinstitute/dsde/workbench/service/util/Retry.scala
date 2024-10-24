package org.broadinstitute.dsde.workbench.service.util

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.FiniteDuration

/**
 */
object Retry extends LazyLogging {

  /**
   * Retry an operation periodically until it returns something or no more
   * retries remain. Returns when the operation returns Some or all retries
   * have been exhausted.
   *
   * @param remainingBackOffIntervals wait intervals between retries
   * @param op operation to retry
   * @return the result of the operation
   */
  def retry[T](remainingBackOffIntervals: Seq[FiniteDuration])(op: => Option[T]): Option[T] =
    op match {
      case Some(x) => Some(x)
      case None =>
        remainingBackOffIntervals match {
          case Nil => None
          case h :: t =>
            logger.info(s"Retrying: ${remainingBackOffIntervals.size} retries remaining, retrying in $h")
            Thread sleep h.toMillis
            retry(t)(op)
        }
    }

  def retryWithPredicate(remainingBackOffIntervals: Seq[FiniteDuration])(op: => Boolean): Boolean =
    op match {
      case true => true
      case false =>
        remainingBackOffIntervals match {
          case Nil => false
          case h :: t =>
            logger.info(s"Retrying: ${remainingBackOffIntervals.size} retries remaining, retrying in $h")
            Thread sleep h.toMillis
            retryWithPredicate(t)(op)
        }
    }

  def retry[T](interval: FiniteDuration, timeout: FiniteDuration, initialDelay: Option[FiniteDuration] = None)(
    op: => Option[T]
  ): Option[T] = {
    initialDelay.foreach(delay => Thread.sleep(delay.toMillis))
    val iterations = (timeout / interval).round.toInt
    retry(Seq.fill(iterations)(interval))(op)
  }

  def retryWithPredicate[T](interval: FiniteDuration,
                            timeout: FiniteDuration,
                            initialDelay: Option[FiniteDuration] = None
  )(
    op: => Boolean
  ): Boolean = {
    initialDelay.foreach(delay => Thread.sleep(delay.toMillis))
    val iterations = (timeout / interval).round.toInt
    retryWithPredicate(Seq.fill(iterations)(interval))(op)
  }
}
