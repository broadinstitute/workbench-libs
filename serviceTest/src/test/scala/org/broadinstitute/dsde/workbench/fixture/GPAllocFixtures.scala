package org.broadinstitute.dsde.workbench.fixture

import org.scalatest.{BeforeAndAfterAll, Suite, TestSuite}

@deprecated(message = "GPAllocSuperFixture is deprecated. It's unnecessary as a mixin; you can remove it entirely (and remove your SuperSuite too!)", since="workbench-service-test-v0.5")
trait GPAllocSuperFixture extends BeforeAndAfterAll {
  self: Suite =>
}

@deprecated(message = "GPAllocFixtures is deprecated, everything you need is now in BillingFixtures", since="workbench-service-test-v0.5")
trait GPAllocFixtures extends BillingFixtures { self: TestSuite =>

}
