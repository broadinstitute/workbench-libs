package org.broadinstitute.dsde.workbench.service.test

import com.typesafe.scalalogging.LazyLogging
import java.util.concurrent.ConcurrentLinkedDeque
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.scalatest.{Outcome, TestSuite, TestSuiteMixin}
import collection.JavaConverters._

/**
  * Mix-in for cleaning up data created during a test.
  */
trait CleanUp extends TestSuiteMixin with ExceptionHandling with LazyLogging { self: TestSuite =>


  private val cleanUpFunctions = new ConcurrentLinkedDeque[() => Any]()


  /**
    * Verb object for the DSL for registering clean-up functions.
    */
  object register {

    /**
      * Register a function to be executed at the end of the test to undo any
      * side effects of the test.
      *
      * @param f the clean-up function
      */
    def cleanUp(f: => Any): Unit = {
      cleanUpFunctions.addFirst(f _)
    }
  }

  /**
    * Function for controlling when the clean-up functions will be run.
    * Clean-up functions are usually run at the end of the test, which includes
    * clean-up from loan-fixture methods. This can cause problems if there are
    * dependencies, such as foreign key references, between test data created
    * in a loan-fixture method and the test itself:
    *
    * <pre>
    * "tries to clean-up parent before child" in {
    *   withParent { parent =>  // withParent loan-fixture clean-up will run before registered clean-up functions
    *     child = Child(parent)
    *     register cleanUp { delete child }
    *     ...
    *   }
    * }
    * </pre>
    *
    * Use withCleanUp to explicitly control when the registered clean-up
    * methods will be called:
    *
    * <pre>
    * "clean-up child before parent" in {
    *   withParent { parent =>
    *     withCleanUp {  // registered clean-up functions will run before enclosing loan-fixture clean-up
    *       child = Child(parent)
    *       register cleanUp { delete child }
    *       ...
    *     }
    *   }
    * }
    * </pre>
    *
    * Note that this is not needed if the dependent objects are contributed by
    * separate loan-fixture methods whose execution order can be explicitly
    * controlled:
    *
    * <pre>
    * "clean-up inner loan-fixtures first" in {
    *   withParent { parent =>
    *     withChild(parent) { child =>
    *       ...
    *     }
    *   }
    * }
    *
    * @param testCode the test code to run
    */
  def withCleanUp(testCode: => Any): Unit = {
    try {
      testCode
    } finally {
      runCleanUpFunctions()
    }
  }

  abstract override def withFixture(test: NoArgTest): Outcome = {
    if (cleanUpFunctions.peek() != null) throw new Exception("cleanUpFunctions non empty at start of withFixture block")
    try {
      super.withFixture(test)
    } finally {
      runCleanUpFunctions()
    }
  }

  private def runCleanUpFunctions() = {
    cleanUpFunctions.asScala.foreach { _() }
    cleanUpFunctions.clear()
  }
}
