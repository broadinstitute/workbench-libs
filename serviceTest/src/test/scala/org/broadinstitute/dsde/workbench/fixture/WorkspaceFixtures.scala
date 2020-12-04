package org.broadinstitute.dsde.workbench.fixture

import org.broadinstitute.dsde.workbench.service._
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.test.RandomUtil
import org.broadinstitute.dsde.workbench.service.test.CleanUp
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.broadinstitute.dsde.workbench.service.util.Util.appendUnderscore
import org.scalatest.TestSuite

import scala.util.Try

/**
 * WorkspaceFixtures
 * Fixtures for creating and cleaning up test workspaces.
 */
trait WorkspaceFixtures extends ExceptionHandling with RandomUtil { self: TestSuite =>

  private def setupWorkspace(namespace: String,
                             workspaceName: String,
                             aclEntries: List[AclEntry],
                             attributes: Option[Map[String, Any]],
                             cleanUp: Boolean
  )(testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    if (aclEntries.nonEmpty)
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
  def withWorkspace(
    namespace: String,
    namePrefix: String,
    authDomain: Set[String] = Set.empty,
    aclEntries: List[AclEntry] = List(),
    attributes: Option[Map[String, Any]] = None,
    cleanUp: Boolean = true,
    workspaceRegion: Option[String] = None
  )(testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val workspaceName = uuidWithPrefix(namePrefix, " ")
    Orchestration.workspaces.create(namespace, workspaceName, authDomain, workspaceRegion)

    setupWorkspace(namespace, workspaceName, aclEntries, attributes, cleanUp)(testCode)
  }

  /**
   * Loan method that creates a workspace and then clones it. Both workspaces will be cleaned
   * up after the test code is run. The workspace names will contain a random and highly
   * likely unique series of characters.
   *
   * @param namespace the namespace for the test workspace
   * @param namePrefix optional prefix for the workspace name
   * @param authDomain optional auth domain for the test workspace
   * @param testCode test code to run
   * @param token auth token for service API calls
   */
  def withClonedWorkspace(
    namespace: String,
    namePrefix: String,
    authDomain: Set[String] = Set.empty,
    aclEntries: List[AclEntry] = List(),
    attributes: Option[Map[String, Any]] = None,
    cleanUp: Boolean = true,
    workspaceRegion: Option[String] = None
  )(testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    // create a workspace
    val workspaceName = uuidWithPrefix(namePrefix, " ")
    Orchestration.workspaces.create(namespace, workspaceName, authDomain, workspaceRegion)

    setupWorkspace(namespace, workspaceName, aclEntries, attributes, cleanUp) { _ =>
      // clone the newly created workspace
      val cloneNamePrefix = appendUnderscore(workspaceName) + "clone"
      val clonedWorkspaceName = uuidWithPrefix(cloneNamePrefix, " ")

      Orchestration.workspaces.clone(namespace, workspaceName, namespace, clonedWorkspaceName, authDomain)
      setupWorkspace(namespace, clonedWorkspaceName, aclEntries, attributes, cleanUp)(testCode)
    }
  }
}
