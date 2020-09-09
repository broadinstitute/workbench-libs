package org.broadinstitute.dsde.workbench.util

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{Future, TimeoutException}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import FutureSupport._
import akka.actor.{ActorSystem, Scheduler}
import akka.testkit.TestKit

class FutureSupportSpec
    extends TestKit(ActorSystem("FutureSupportSpec"))
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {
  import system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  "toFutureTry" should "turn a successful Future into a successful Future(Success)" in {
    val successFuture = Future.successful(2)

    whenReady(successFuture.toTry) { t =>
      t.success.value shouldBe 2
    }
  }

  it should "turn a failed Future into a successful Future(Failure)" in {
    val failFuture: Future[Unit] = Future.failed(new RuntimeException)

    whenReady(failFuture.toTry) { t =>
      t shouldBe a[Failure[_]]
    }
  }

  "assertSuccessfulTries" should "return a successful Future when given an empty map" in {
    whenReady(assertSuccessfulTries(Map.empty[Int, Try[Int]])) { m =>
      m shouldBe empty
    }
  }

  it should "return a successful Future when all contained tries are successful" in {
    val tries = Map(1 -> Success(2), 2 -> Success(3))
    whenReady(assertSuccessfulTries(tries)) { m =>
      m shouldBe Map(1 -> 2, 2 -> 3)
    }
  }

  it should "return a failed Future when any contained Try is a failure" in {
    val tries = Map(1 -> Success(2), 2 -> Failure(new RuntimeException))
    whenReady(assertSuccessfulTries(tries).failed) { f =>
      f shouldBe a[RuntimeException]
    }
  }

  "withTimeout" should "return the future if it completes quickly enough" in {
    val theFuture = Future { Thread.sleep(100); 42 }
    whenReady(theFuture.withTimeout(200 milliseconds, "timeout")) { f =>
      f shouldBe 42
    }
  }

  it should "timeout if the future takes too long" in {
    val theFuture = Future { Thread.sleep(200); 42 }
    whenReady(theFuture.withTimeout(100 milliseconds, "timeout").failed) { f =>
      f shouldBe a[TimeoutException]
      f.getMessage shouldBe "timeout"
    }
  }
}
