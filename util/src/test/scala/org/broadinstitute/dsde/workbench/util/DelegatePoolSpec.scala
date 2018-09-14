package org.broadinstitute.dsde.workbench.util

import java.util.UUID

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class DelegatePoolSpec extends FlatSpec with BeforeAndAfterAll with Matchers {
  "DelegatePool" should "delegate" in {
    val poolSize = 10
    val trials = 1000

    val delegates = for(_ <- 1 to poolSize) yield new PoolClass(UUID.randomUUID().toString)
    val delegatePool = DelegatePool[PoolTrait](delegates)

    val delegatesHit = for(i <- 1 to trials) yield {
      val (probe, id) = delegatePool.test(i)
      probe should equal(i)
      id
    }

    val delegatesHitGrouped = delegatesHit.groupBy(x=>x)

    // every delegate in the pool should have been hit
    delegatesHitGrouped.size should equal(poolSize)

    // every delegate in the pool should have been hit enough, let's say 1/4 of expectation
    delegatesHitGrouped.values.forall(_.size > trials/poolSize/4) should be(true)
  }
}

trait PoolTrait {
  val id: String
  def test(x: Int): (Int, String) = (x, id)
}

case class PoolClass(id: String) extends PoolTrait
