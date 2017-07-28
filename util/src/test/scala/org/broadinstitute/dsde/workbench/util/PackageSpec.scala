package org.broadinstitute.dsde.workbench.util

import org.scalatest.{FlatSpecLike, Matchers}
import scala.concurrent.duration._

class PackageSpec extends FlatSpecLike with Matchers {

  "addJitter" should "not add more than 10% jitter" in {
    addJitter(0.5 seconds) shouldBe <= (0.55 seconds)
    addJitter(5 seconds) shouldBe <= (5.5 seconds)
    
    //for >10s, the max jitter is 1s
    addJitter(15 seconds) shouldBe <= (16 seconds)
  }
  
  "toScalaDuration" should "roundtrip" in {
    val nanos = scala.util.Random.nextLong
    toScalaDuration( java.time.Duration.ofNanos(nanos) ).toNanos shouldBe nanos
  }
}
