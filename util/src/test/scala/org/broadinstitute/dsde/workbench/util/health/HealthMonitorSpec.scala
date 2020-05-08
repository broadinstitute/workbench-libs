package org.broadinstitute.dsde.workbench.util.health

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import org.broadinstitute.dsde.workbench.util.health.Subsystems.Agora
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpecLike
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class HealthMonitorSpec
    extends TestKit(ActorSystem("HealthMonitorSpec"))
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers {
  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  import system.dispatcher
  implicit val askTimeout = Timeout(5 seconds)

  "HealthMonitor" should "monitor health" in {
    val callCount = new AtomicInteger(0)

    // simulate a subsystem test function
    def testCheck() = Future {
      val count = callCount.incrementAndGet()
      if (count > 5 && count <= 10) {
        throw new WorkbenchException("subsystem failed")
      } else {
        HealthMonitor.OkStatus
      }
    }

    // instantiate health monitor actor
    val healthMonitorRef = system.actorOf(HealthMonitor.props(Set(Agora)) { () =>
      Map(Agora -> testCheck())
    })

    assertResult(StatusCheckResponse(false, Map(Agora -> HealthMonitor.UnknownStatus))) {
      Await.result(healthMonitorRef ? HealthMonitor.GetCurrentStatus, Duration.Inf)
    }

    // setup scheduler to call HealthMonitor.CheckAll
    system.scheduler.schedule(100 milliseconds, 100 milliseconds, healthMonitorRef, HealthMonitor.CheckAll)

    awaitAssert(
      assertResult(StatusCheckResponse(true, Map(Agora -> HealthMonitor.OkStatus))) {
        Await.result(healthMonitorRef ? HealthMonitor.GetCurrentStatus, Duration.Inf)
      },
      1 second,
      10 milliseconds
    )

    awaitAssert(
      assertResult(StatusCheckResponse(false, Map(Agora -> HealthMonitor.failedStatus("subsystem failed")))) {
        Await.result(healthMonitorRef ? HealthMonitor.GetCurrentStatus, Duration.Inf)
      },
      1 second,
      10 milliseconds
    )

    awaitAssert(
      assertResult(StatusCheckResponse(true, Map(Agora -> HealthMonitor.OkStatus))) {
        Await.result(healthMonitorRef ? HealthMonitor.GetCurrentStatus, Duration.Inf)
      },
      1 second,
      10 milliseconds
    )
  }

  it should "handle timeouts" in {
    // simulate a subsystem test function
    def testCheck() = Future {
      Thread.sleep(1000) // take too long to force a timeout
      HealthMonitor.OkStatus
    }

    val futureTimeout = 100 milliseconds
    // instantiate health monitor actor
    val healthMonitorRef = system.actorOf(HealthMonitor.props(Set(Agora), futureTimeout) { () =>
      Map(Agora -> testCheck())
    })

    // just send 1 message - no need for scheduler in this test
    healthMonitorRef ! HealthMonitor.CheckAll

    awaitAssert(
      assertResult(
        StatusCheckResponse(
          false,
          Map(
            Agora -> HealthMonitor
              .failedStatus(s"Timed out after ${futureTimeout.toString} waiting for a response from ${Agora.toString}")
          )
        )
      ) {
        Await.result(healthMonitorRef ? HealthMonitor.GetCurrentStatus, Duration.Inf)
      },
      1 second,
      10 milliseconds
    )
  }

  it should "handle stale status" in {
    // simulate a subsystem test function
    def testCheck() = Future {
      HealthMonitor.OkStatus
    }

    // instantiate health monitor actor
    val healthMonitorRef = system.actorOf(HealthMonitor.props(Set(Agora), staleThreshold = 500 milliseconds) { () =>
      Map(Agora -> testCheck())
    })

    // assert it starts in unknown state
    assertResult(StatusCheckResponse(false, Map(Agora -> HealthMonitor.UnknownStatus))) {
      Await.result(healthMonitorRef ? HealthMonitor.GetCurrentStatus, Duration.Inf)
    }

    // just send 1 message - no need for scheduler in this test
    healthMonitorRef ! HealthMonitor.CheckAll

    // assert that it switches to ok
    awaitAssert(
      assertResult(StatusCheckResponse(true, Map(Agora -> HealthMonitor.OkStatus))) {
        Await.result(healthMonitorRef ? HealthMonitor.GetCurrentStatus, Duration.Inf)
      },
      1 second,
      10 milliseconds
    )

    // assert that it eventually switches back to unknown since it has not polled anymore so ok state should be stale
    awaitAssert(
      assertResult(StatusCheckResponse(false, Map(Agora -> HealthMonitor.UnknownStatus))) {
        Await.result(healthMonitorRef ? HealthMonitor.GetCurrentStatus, Duration.Inf)
      },
      1 second,
      10 milliseconds
    )
  }
}
