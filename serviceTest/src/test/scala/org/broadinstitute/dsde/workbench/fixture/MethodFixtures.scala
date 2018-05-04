package org.broadinstitute.dsde.workbench.fixture

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.Orchestration
import org.broadinstitute.dsde.workbench.service.test.RandomUtil
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.broadinstitute.dsde.workbench.service.util.Util.{appendUnderscore, makeUuid}
import org.scalatest.TestSuite

import scala.util.{Failure, Success, Try}

trait MethodFixtures extends ExceptionHandling with RandomUtil { self: TestSuite =>

  def withMethod(testName:String, method:Method, numSnapshots: Int = 1, cleanUp: Boolean = true)
                (testCode: (String) => Any)
                (implicit token: AuthToken): Unit = {
    // create a method
    val methodName: String = appendUnderscore(testName) + makeUuid

    Try {
      for (i <- 1 to numSnapshots)
        Orchestration.methods.createMethod(method.creationAttributes + ("name" -> methodName))
    } match {
      case Success(s) =>
        try {
          testCode(methodName)
        } finally {
          if (cleanUp) {
            try {
              for (i <- 1 to numSnapshots)
                Orchestration.methods.redact(method.methodNamespace, methodName, i)
            } catch nonFatalAndLog(s"Error redacting method $method.methodName/$methodName")
          }
        }
      case Failure(f) =>
        logger.error("withMethod() throws exception: ", f)
        fail("withMethod() throws exception: ", f) // end test
    }



  }

  def withMethod(methodName: String)
                (testCode: ((String, String)) => Any)
                (implicit token: AuthToken): Unit = {
    val name = methodName + randomUuid
    val attributes = MethodData.SimpleMethod.creationAttributes + ("name" -> name)
    val namespace = attributes("namespace")
    Orchestration.methods.createMethod(attributes)

    try {
      testCode((name, namespace))
    } catch {
      case t: Exception =>
        logger.error("MethodFixtures.withMethod Exception: ", t)
        throw t // end test execution
    } finally {

    }

  }
}
