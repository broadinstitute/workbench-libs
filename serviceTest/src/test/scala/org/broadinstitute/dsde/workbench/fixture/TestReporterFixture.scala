package org.broadinstitute.dsde.workbench.fixture

import org.scalatest.{Args, Status, TestSuite, TestSuiteMixin}

trait TestReporterFixture extends TestSuiteMixin { self: TestSuite =>

  abstract override def run(testName: Option[String], args: Args): Status = {
    val rep = TestEventReporter(args.reporter)
    super.run(testName, args.copy(reporter = rep))
  }

}
