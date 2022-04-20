package org.broadinstitute.dsde.workbench.fixture

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.Orchestration
import org.broadinstitute.dsde.workbench.service.test.RandomUtil
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.scalatest.TestSuite

trait MethodFixtures extends ExceptionHandling with RandomUtil { self: TestSuite =>

  def withMethod(testName: String, method: Method, numSnapshots: Int = 1, cleanUp: Boolean = true)(
    testCode: (String) => Any
  )(implicit token: AuthToken): Unit = {
    // create a method
    val methodName: String = uuidWithPrefix(testName)
    for (_ <- 1 to numSnapshots)
      Orchestration.methods.createMethod(method.creationAttributes + ("name" -> methodName))
    try testCode(methodName)
    catch {
      case t: Exception =>
        logger.error("MethodFixtures.withMethod Exception: ", t)
        throw t // end test execution
    } finally
      if (cleanUp) {
        try
          for (i <- 1 to numSnapshots)
            Orchestration.methods.redact(method.methodNamespace, methodName, i)
        catch nonFatalAndLog(s"Error redacting method $method.methodName/$methodName")
      }

  }

  def withMethod(prefix: String)(testCode: ((String, String)) => Any)(implicit token: AuthToken): Unit = {
    val name = uuidWithPrefix(prefix)
    val namespace = MethodData.SimpleMethod.creationAttributes.get("namespace").head + randomUuid
    val attributes = MethodData.SimpleMethod.creationAttributes ++ Map("name" -> name, "namespace" -> namespace)
    Orchestration.methods.createMethod(attributes)

    try testCode((name, namespace))
    catch {
      case t: Exception =>
        logger.error("MethodFixtures.withMethod Exception: ", t)
        throw t // end test execution
    }
  }
}
