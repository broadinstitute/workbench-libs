package org.broadinstitute.dsde.workbench.fixture

import com.typesafe.scalalogging.LazyLogging
import org.scalatest._

trait FailedTestRetryable extends TestSuiteMixin with Retries with LazyLogging { this: TestSuite =>

  abstract override def withFixture(test: NoArgTest): Outcome =
    if (isRetryable(test)) {
      withRetryOnFailure {
        super.withFixture(test)
      }
    } else {
      super.withFixture(test)
    }

}
