package org.broadinstitute.dsde.workbench.health

import java.util.concurrent.TimeoutException

import akka.actor.{Actor, Props}
import akka.pattern.{after, pipe}
import cats._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.health.Subsystems.Subsystem

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import HealthMonitor._

/**
  * Created by rtitle on 5/17/17.
  */
object HealthMonitor {
  val DefaultFutureTimeout = 1 minute
  val DefaultStaleThreshold = 15 minutes

  val OkStatus = SubsystemStatus(true, None)
  val UnknownStatus = SubsystemStatus(false, Some(List("Unknown status")))
  def failedStatus(message: String) = SubsystemStatus(false, Some(List(message)))

  // Actor API:

  sealed trait HealthMonitorMessage
  /** Triggers subsystem checking */
  case object CheckAll extends HealthMonitorMessage
  /** Stores status for a particular subsystem */
  case class Store(subsystem: Subsystem, status: SubsystemStatus) extends HealthMonitorMessage
  /** Retrieves current status and sends back to caller */
  case object GetCurrentStatus extends HealthMonitorMessage

  def props(subsystems: Set[Subsystem], futureTimeout: FiniteDuration = DefaultFutureTimeout, staleThreshold: FiniteDuration = DefaultStaleThreshold)
           (checkHealth: () => Traversable[(Subsystem, Future[SubsystemStatus])]): Props =
    Props(new HealthMonitor(subsystems, futureTimeout, staleThreshold)(checkHealth))
}

/**
  * This actor periodically checks the health of each subsystem and reports on the results.
  * It is used for system monitoring.
  *
  * For a list of the subsystems, see the [[Subsystem]] enum.
  *
  * The actor lifecyle is as follows:
  * 1. Periodically receives a [[CheckAll]] message from the Akka scheduler. Receipt of this message
  * triggers independent, asynchronous checks of each subsystem. The results of these futures
  * are piped to self via...
  *
  * 2. the [[Store]] message. This updates the actor state for the given subsystem status. Note the current
  * timestamp is also stored to ensure the returned statuses are current (see staleThreshold param).
  *
  * 3. [[GetCurrentStatus]] looks up the current actor state and sends it back to the caller wrapped in
  * a [[StatusCheckResponse]] case class. This message is purely for retrieving state; it does not
  * trigger any asynchronous operations.
  *
  * Note we structure status checks in this asynchronous way for a couple of reasons:
  * - The /status endpoint is unauthenticated - we don't want each call to /status to trigger DB queries,
  * Google calls, etc. It opens us up to DDoS.
  *
  * - The /status endpoint should be reliable, and decoupled from the status checks themselves. For example,
  * if there is a problem with the DB that is causing hanging queries, that behavior shouldn't leak out to
  * the /status API call. Instead, the call to /status will return quickly and report there is a problem
  * with the DB (but not other subsystems because the checks are independent).
  *
  * See HealthMonitorSpec for usage examples. Be sure to notice use of system.scheduler.schedule.
  *
  * @param subsystems the list of all the subsystems this instance should care about
  * @param futureTimeout amount of time after which subsystem check futures will time out (default 1 minute)
  * @param staleThreshold amount of time after which statuses are considered "stale". If a status is stale
  *                       then it won't be returned to the caller; instead a failing status with an "Unknown"
  *                       message will be returned. This shouldn't normally happen in practice if we have
  *                       reasonable future timeouts; however it is still a defensive check in case something
  *                       unexpected goes wrong. Default 15 minutes.
  */
class HealthMonitor private (val subsystems: Set[Subsystem], val futureTimeout: FiniteDuration, val staleThreshold: FiniteDuration)
                            (checkHealth: () => Traversable[(Subsystem, Future[SubsystemStatus])]) extends Actor with LazyLogging {
  // Use the execution context for this actor's dispatcher for all asynchronous operations.
  // We define a separate execution context (a fixed thread pool) for health checking to
  // not interfere with user facing operations.
  import context.dispatcher

  /**
    * Contains each subsystem status along with a timestamp of when the entry was made.
    * Initialized with unknown status.
    */
  private var data: Map[Subsystem, (SubsystemStatus, Long)] = {
    val now = System.currentTimeMillis
    subsystems.map(_ -> (UnknownStatus, now)).toMap
  }

  override def receive: Receive = {
    case CheckAll => checkAll
    case Store(subsystem, status) => store(subsystem, status)
    case GetCurrentStatus => sender ! getCurrentStatus
  }

  private def checkAll: Unit = {
    checkHealth().foreach(processSubsystemResult)
  }

  /**
    * A monoid used for combining SubsystemStatuses.
    * Zero is an ok status with no messages.
    * Append uses && on the ok flag, and ++ on the messages.
    */
  implicit val SubsystemStatusMonoid = new Monoid[SubsystemStatus] {
    def combine(a: SubsystemStatus, b: SubsystemStatus): SubsystemStatus = {
      SubsystemStatus(a.ok && b.ok, a.messages |+| b.messages)
    }
    def empty: SubsystemStatus = OkStatus
  }

  private def processSubsystemResult(subsystemAndResult: (Subsystem, Future[SubsystemStatus])): Unit = {
    val (subsystem, result) = subsystemAndResult
    result.withTimeout(futureTimeout, s"Timed out after ${futureTimeout.toString} waiting for a response from ${subsystem.toString}")
    .recover { case NonFatal(ex) =>
      failedStatus(ex.getMessage)
    } map {
      Store(subsystem, _)
    } pipeTo self
  }

  private def store(subsystem: Subsystem, status: SubsystemStatus): Unit = {
    data = data + (subsystem -> (status, System.currentTimeMillis))
    logger.debug(s"New health monitor state: $data")
  }

  private def getCurrentStatus: StatusCheckResponse = {
    val now = System.currentTimeMillis()
    // Convert any expired statuses to unknown
    val processed = data.mapValues {
      case (_, t) if now - t > staleThreshold.toMillis => UnknownStatus
      case (status, _) => status
    }
    // overall status is ok iff all subsystems are ok
    val overall = processed.forall(_._2.ok)
    StatusCheckResponse(overall, processed)
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
  private implicit class FutureWithTimeout[A](f: Future[A]) {
    def withTimeout(duration: FiniteDuration, errMsg: String): Future[A] =
      Future.firstCompletedOf(Seq(f, after(duration, context.system.scheduler)(Future.failed(new TimeoutException(errMsg)))))
  }
}