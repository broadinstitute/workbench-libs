package org.broadinstitute.dsde.workbench.util

import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.scalatest.TryValues._
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

import FutureSupport._

class FutureSupportSpec extends FlatSpecLike with BeforeAndAfterAll with Matchers with ScalaFutures {
  "toFutureTry" should "turn a successful Future into a successful Future(Success)" in {
    val successFuture = Future.successful(2)
    
    whenReady( toFutureTry(successFuture) ) { t =>
      t.success.value shouldBe 2
    }
  }
  
  "toFutureTry" should "turn a failed Future into a successful Future(Failure)" in {
    val failFuture = Future.failed(new RuntimeException)
    
    whenReady( toFutureTry(failFuture) ) { t =>
      t shouldBe a [Failure[_]]
    }
  }
  
  "assertSuccessfulTries" should "return a successful Future when given an empty map" in {
    whenReady( assertSuccessfulTries(Map.empty[Int, Try[Int]]) ) { m =>
      m shouldBe empty
    }
  }
  
  "assertSuccessfulTries" should "return a successful Future when all contained tries are successful" in {
    val tries = Map( 1 -> Success(2), 2 -> Success(3) )
    whenReady( assertSuccessfulTries(tries) ) { m =>
      m shouldBe Map( 1 -> 2, 2 -> 3 )
    }
  }
  
  "assertSuccessfulTries" should "return a failed Future when any contained Try is a failure" in {
    val tries = Map( 1 -> Success(2), 2 -> Failure(new RuntimeException) )
    whenReady( assertSuccessfulTries(tries).failed ) { f =>
      f shouldBe a [RuntimeException]
    }
  }
}
