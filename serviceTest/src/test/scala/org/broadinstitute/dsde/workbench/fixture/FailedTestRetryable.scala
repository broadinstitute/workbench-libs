package org.broadinstitute.dsde.workbench.fixture

import com.typesafe.scalalogging.LazyLogging
import org.scalatest._

trait FailedTestRetryable extends TestSuiteMixin with Retries with LazyLogging { this: TestSuite =>

  abstract override def withFixture(test: NoArgTest): Outcome = {
    super.withFixture(test) match {
      case failed: Failed =>
        if (isRetryable(test)) {
          logger.warn(s"About to retry failed test -- " + test.name)
          withRetryOnFailure(super.withFixture(test))
        } else {
          super.withFixture(test) // don't retry if not tagged with `taggedAs(Retryable)` even if test failed
        }
      case other => other
    }
  }

}
