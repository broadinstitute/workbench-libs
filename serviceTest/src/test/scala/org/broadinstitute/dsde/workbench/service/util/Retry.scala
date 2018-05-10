package org.broadinstitute.dsde.workbench.service.util

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.duration.{Deadline, DurationInt, FiniteDuration}

/**
  */
object Retry extends LazyLogging {

  /**
    * Retry an operation periodically.
    * Returns when the operation returns Some or all retries
    * have been exhausted or reached end of maximum wait time.
    *
    * @param remainingBackOffIntervals wait intervals between retries
    * @param op operation to retry
    * @return the result of the operation
    */
  def retry[T](remainingBackOffIntervals: Seq[FiniteDuration], maxTime: FiniteDuration = 1.minute)(op: => Option[T]): Option[T] = {
    val deadline: Deadline = maxTime.fromNow
    op match {
      case Some(x) => Some(x)
      case None => remainingBackOffIntervals match {
        case Nil => None
        case h :: t =>
          logger.info(s"Retrying: ${remainingBackOffIntervals.size} retries remaining, retrying in $h")
          Thread sleep h.toMillis
          if (deadline.isOverdue()) {
            retry(Nil, 0.seconds)(op)
          } else {
            retry(t, deadline.timeLeft)(op)
          }
      }
    }
  }

  def retry[T](interval: FiniteDuration, timeout: FiniteDuration)(op: => Option[T]): Option[T] = {
    val iterations = (timeout / interval).round.toInt
    retry(Seq.fill(iterations)(interval), timeout)(op)
  }
}
