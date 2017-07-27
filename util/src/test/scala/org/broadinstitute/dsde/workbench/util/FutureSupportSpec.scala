package org.broadinstitute.dsde.workbench.util

import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.scalatest.TryValues._
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.{ExecutionContext, Future}
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
      t should be a 'failure
	}
  }
}
