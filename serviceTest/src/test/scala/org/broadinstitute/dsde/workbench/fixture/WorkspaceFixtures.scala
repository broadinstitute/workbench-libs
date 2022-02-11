package org.broadinstitute.dsde.workbench.fixture

import cats.effect.{Resource, Sync}
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service._
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, RandomUtil}
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.broadinstitute.dsde.workbench.service.util.Util.appendUnderscore

import scala.util.Try

/**
 * WorkspaceFixtures
 * Fixtures for creating and cleaning up test workspaces.
 */
object WorkspaceFixtures extends ExceptionHandling with RandomUtil {

  /**
   * Cleans up the workspace once the test code has been run
   */
  private def runTestAndCleanUpWorkspace(namespace: String, workspaceName: String, cleanUp: Boolean)(
    testCode: (String) => Any
  )(implicit token: AuthToken): Unit = {
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
   * @param namePrefix prefix for the workspace name
   * @param authDomain optional auth domain for the test workspace
   * @param aclEntries optional access level entries for the workspace
   * @param attributes optional workspace attributes
   * @param cleanUp optional boolean parameter to indicate whether to delete the workspace. Default is true
   * @param bucketLocation optional region where the bucket associated with workspace should be created
   * @param testCode test code to run
   */
  def withWorkspace(
    namespace: String,
    namePrefix: String,
    authDomain: Set[String] = Set.empty,
    aclEntries: List[AclEntry] = List(),
    attributes: Option[Map[String, Any]] = None,
    cleanUp: Boolean = true,
    bucketLocation: Option[String] = None
  )(testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val workspaceName = uuidWithPrefix(namePrefix, " ")
    Orchestration.workspaces.create(namespace, workspaceName, authDomain, bucketLocation)

    if (aclEntries.nonEmpty)
      Orchestration.workspaces.updateAcl(namespace, workspaceName, aclEntries)
    if (attributes.isDefined)
      Orchestration.workspaces.setAttributes(namespace, workspaceName, attributes.get)

    runTestAndCleanUpWorkspace(namespace, workspaceName, cleanUp)(testCode)
  }

  /**
   * Loan method that creates a workspace and then clones it. Both workspaces will be cleaned
   * up after the test code is run. The workspace names will contain a random and highly
   * likely unique series of characters.
   *
   * @param namespace the namespace for the test workspace
   * @param namePrefix prefix for the workspace name
   * @param authDomain optional auth domain for the test workspace
   * @param aclEntries optional access level entries for the workspace
   * @param attributes optional workspace attributes
   * @param cleanUp optional boolean parameter to indicate whether to delete the workspace. Default is true
   * @param bucketLocation optional region where the bucket associated with workspace should be created
   * @param testCode test code to run
   */
  def withClonedWorkspace(
    namespace: String,
    namePrefix: String,
    authDomain: Set[String] = Set.empty,
    aclEntries: List[AclEntry] = List(),
    attributes: Option[Map[String, Any]] = None,
    cleanUp: Boolean = true,
    bucketLocation: Option[String] = None
  )(testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    // create a workspace
    val workspaceName = uuidWithPrefix(namePrefix, " ")
    Orchestration.workspaces.create(namespace, workspaceName, authDomain, bucketLocation)

    // set up the original workspace
    if (aclEntries.nonEmpty)
      Orchestration.workspaces.updateAcl(namespace, workspaceName, aclEntries)
    if (attributes.isDefined)
      Orchestration.workspaces.setAttributes(namespace, workspaceName, attributes.get)

    runTestAndCleanUpWorkspace(namespace, workspaceName, cleanUp) { _ =>
      // clone the newly created workspace
      val cloneNamePrefix = appendUnderscore(workspaceName) + "clone"
      val clonedWorkspaceName = uuidWithPrefix(cloneNamePrefix, " ")

      Orchestration.workspaces.clone(namespace, workspaceName, namespace, clonedWorkspaceName, authDomain)
      runTestAndCleanUpWorkspace(namespace, clonedWorkspaceName, cleanUp)(testCode)
    }
  }

  /**
   * Create a workspace `Resource` whose lifetime is bound to the `Resource`'s `use` method.
   *
   * @param billingProjectName Billing project for workspace (or workspace namespace).
   * @param namePrefix         Prefix for workspace name. [default "tmp-workspace-"]
   * @param authDomain         Optional auth domain for workspace
   * @param bucketLocation     Optional region where workspace bucket should be created
   * @param creatorAuthToken   Auth token of workspace creator
   */
  def resource[F[_]](billingProjectName: String,
                     namePrefix: String = "tmp-workspace-",
                     authDomain: Set[String] = Set.empty,
                     bucketLocation: Option[String] = None
  )(implicit creatorAuthToken: AuthToken, F: Sync[F]): Resource[F, String] = {
    def createWorkspace: F[String] = F.delay {
      val workspaceName = randomIdWithPrefix(namePrefix)
      Orchestration.workspaces.create(billingProjectName, workspaceName, authDomain, bucketLocation)
      workspaceName
    }

    def destroyWorkspace(workspaceName: String): F[Unit] = F.delay {
      Orchestration.workspaces.delete(billingProjectName, workspaceName)
    }

    Resource.make(createWorkspace)(destroyWorkspace)
  }
}
