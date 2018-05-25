package org.broadinstitute.dsde.workbench.fixture

import org.broadinstitute.dsde.workbench.service._
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.test.RandomUtil
import org.broadinstitute.dsde.workbench.service.test.CleanUp
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.broadinstitute.dsde.workbench.service.util.Util.appendUnderscore
import org.scalatest.TestSuite

import scala.util.Try

/**WorkspaceFixtures
  * Fixtures for creating and cleaning up test workspaces.
  */
trait WorkspaceFixtures extends ExceptionHandling with RandomUtil { self: TestSuite =>

  /**
    * Loan method that creates a workspace that will be cleaned up after the
    * test code is run. The workspace name will contain a random and highly
    * likely unique series of characters.
    *
    * @param namespace the namespace for the test workspace
    * @param namePrefix optional prefix for the workspace name
    * @param authDomain optional auth domain for the test workspace
    * @param testCode test code to run
    * @param token auth token for service API calls
    */
  def withWorkspace(namespace: String, namePrefix: String, authDomain: Set[String] = Set.empty,
                    aclEntries: List[AclEntry] = List(),
                    attributes: Option[Map[String, Any]] = None,
                    cleanUp: Boolean = true)
                   (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val workspaceName = uuidWithPrefix(namePrefix, " ")
    Orchestration.workspaces.create(namespace, workspaceName, authDomain)
    Orchestration.workspaces.updateAcl(namespace, workspaceName, aclEntries)
    if (attributes.isDefined)
      Orchestration.workspaces.setAttributes(namespace, workspaceName, attributes.get)
    val testTrial = Try {
      testCode(workspaceName)
    }

    val cleanupTrial = Try {
      if (cleanUp) {
        Orchestration.workspaces.delete(namespace, workspaceName)
      }
    }

    CleanUp.runCodeWithCleanup(testTrial, cleanupTrial)
  }

  def withClonedWorkspace(namespace: String, namePrefix: String, authDomain: Set[String] = Set.empty)
                         (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    withWorkspace(namespace, namePrefix, authDomain) { _ =>
      val cloneNamePrefix = appendUnderscore(namePrefix) + "clone"
      withWorkspace(namespace, cloneNamePrefix, authDomain)(testCode)
    }
  }
}
