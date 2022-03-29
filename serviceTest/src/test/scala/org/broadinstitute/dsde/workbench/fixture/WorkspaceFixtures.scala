package org.broadinstitute.dsde.workbench.fixture

import cats.Applicative
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource, Sync}
import cats.implicits.toFoldableOps
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service._
import org.broadinstitute.dsde.workbench.service.test.RandomUtil

/**
 * WorkspaceFixtures
 * Fixtures for creating and cleaning up test workspaces.
 */
object WorkspaceFixtures extends RandomUtil {

  /**
   * Loan method that creates a workspace that will be cleaned up after the
   * test code is run. The workspace name will contain a random and highly
   * likely unique series of characters.
   *
   * @param namespace      the namespace for the test workspace
   * @param namePrefix     optional prefix for the workspace name
   * @param authDomain     optional auth domain for the test workspace
   * @param aclEntries     optional access level entries for the workspace
   * @param attributes     optional workspace attributes
   * @param cleanUp        optional boolean parameter to indicate whether to delete the workspace. Default is true
   * @param bucketLocation optional region where the bucket associated with workspace should be created
   * @param testCode       test code to run
   */
  def withTemporaryWorkspace[A](namespace: String,
                                namePrefix: Option[String] = None,
                                authDomain: Option[Set[String]] = None,
                                aclEntries: Option[List[AclEntry]] = None,
                                attributes: Option[Map[String, Any]] = None,
                                bucketLocation: Option[String] = None,
                                cleanUp: Boolean = true
  )(testCode: (String) => A)(implicit token: AuthToken): A =
    WorkspaceFixtures
      .temporaryWorkspace[IO](namespace, token, namePrefix, authDomain, aclEntries, attributes, bucketLocation, cleanUp)
      .use(workspaceName => IO.delay(testCode(workspaceName)))
      .unsafeRunSync

  /**
   * Loan method that creates a workspace and then clones it. Both workspaces will be cleaned
   * up after the test code is run. The workspace names will contain a random and highly
   * likely unique series of characters.
   *
   * @param namespace      the namespace for the test workspace
   * @param namePrefix     prefix for the workspace name
   * @param authDomain     optional auth domain for the test workspace
   * @param aclEntries     optional access level entries for the workspace
   * @param attributes     optional workspace attributes
   * @param cleanUp        optional boolean parameter to indicate whether to delete the workspace. Default is true
   * @param bucketLocation optional region where the bucket associated with workspace should be created
   * @param testCode       test code to run
   */
  def withTemporaryWorkspaceClone[A](namespace: String,
                                     namePrefix: Option[String] = None,
                                     authDomain: Option[Set[String]] = None,
                                     aclEntries: Option[List[AclEntry]] = None,
                                     attributes: Option[Map[String, Any]] = None,
                                     bucketLocation: Option[String] = None,
                                     cleanUp: Boolean = true
  )(testCode: (String) => A)(implicit token: AuthToken): A = {
    val temporaryClone = for {
      workspaceName <- WorkspaceFixtures.temporaryWorkspace[IO](
        namespace,
        token,
        namePrefix = namePrefix,
        authDomain = authDomain,
        aclEntries = aclEntries,
        attributes = attributes,
        bucketLocation = bucketLocation,
        cleanUp = cleanUp
      )

      clone <- WorkspaceFixtures.temporaryWorkspaceClone[IO](
        workspaceBillingProject = namespace,
        workspaceName = workspaceName,
        cloneBillingProject = namespace,
        creatorAuthToken = token,
        namePrefix = namePrefix.map(_ + "-clone-"),
        authDomain = authDomain,
        cleanUp = cleanUp
      )
    } yield clone

    temporaryClone
      .use(clonedWorkspaceName => IO.delay(testCode(clonedWorkspaceName)))
      .unsafeRunSync
  }

  /**
   * Create a temporary workspace `Resource` whose lifetime is bound to the `Resource`'s `use` method.
   *
   * @param billingProjectName Billing project (or namespace) for workspace.
   * @param creatorAuthToken   Access token of the workspace creator.
   * @param namePrefix         Prefix for workspace name.                    [default "tmp-workspace-"]
   * @param authDomain         Set of members group names for auth domain.   [default Set.empty]
   * @param aclEntries         Access Control List entries.                  [default None]
   * @param attributes         Optional workspace attributes.                [default None]
   * @param bucketLocation     Region where the workspace should be created. [default None]
   * @param cleanUp            Delete the workspace after use.               [default true]
   */
  def temporaryWorkspace[F[_]](billingProjectName: String,
                               creatorAuthToken: AuthToken,
                               namePrefix: Option[String] = None,
                               authDomain: Option[Set[String]] = None,
                               aclEntries: Option[List[AclEntry]] = None,
                               attributes: Option[Map[String, Any]] = None,
                               bucketLocation: Option[String] = None,
                               cleanUp: Boolean = true
  )(implicit F: Sync[F]): Resource[F, String] = {
    def createWorkspace: F[String] = F.delay {
      val workspaceName = randomIdWithPrefix(namePrefix.getOrElse("tmp-workspace-"))
      Orchestration.workspaces.create(
        billingProjectName,
        workspaceName,
        authDomain.getOrElse(Set.empty),
        bucketLocation
      )(creatorAuthToken)
      workspaceName
    }

    def destroyWorkspace(workspaceName: String): F[Unit] =
      Applicative[F].whenA(cleanUp) {
        F.delay {
          Orchestration.workspaces.delete(billingProjectName, workspaceName)(creatorAuthToken)
        }
      }

    for {
      workspaceName <- Resource.make(createWorkspace)(destroyWorkspace)
      _ <- aclEntries.traverse_ { entries =>
        Resource.eval(F.delay {
          Orchestration.workspaces.updateAcl(billingProjectName, workspaceName, entries)(creatorAuthToken)
        })
      }
      _ <- attributes.traverse_ { attrs =>
        Resource.eval(F.delay {
          Orchestration.workspaces.setAttributes(billingProjectName, workspaceName, attrs)(creatorAuthToken)
        })
      }
    } yield workspaceName
  }

  /**
   * Create a temporary workspace clone `Resource` whose lifetime is bound to the `Resource`'s `use` method.
   *
   * @param workspaceBillingProject Billing project (or namespace) of workspace to clone.
   * @param workspaceName           Name of workspace to clone.
   * @param cloneBillingProject     Billing project (or namespace) for clone.
   * @param creatorAuthToken        Access token of the workspace creator
   * @param namePrefix              Prefix for clone name.                       [default workspaceName + "-clone-"]
   * @param authDomain              Set of members group names for auth domain.  [default Set.empty]
   * @param cleanUp                 Delete the clone after use.                  [default true]
   */
  def temporaryWorkspaceClone[F[_]](workspaceBillingProject: String,
                                    workspaceName: String,
                                    cloneBillingProject: String,
                                    creatorAuthToken: AuthToken,
                                    namePrefix: Option[String] = None,
                                    authDomain: Option[Set[String]] = None,
                                    cleanUp: Boolean = true
  )(implicit F: Sync[F]): Resource[F, String] = {
    def cloneWorkspace: F[String] = F.delay {
      val cloneName = randomIdWithPrefix(namePrefix.getOrElse(workspaceName + "-clone-"))
      Orchestration.workspaces.clone(
        workspaceBillingProject,
        workspaceName,
        cloneBillingProject,
        cloneName,
        authDomain.getOrElse(Set.empty)
      )(creatorAuthToken)
      cloneName
    }

    def destroyWorkspace(workspaceName: String): F[Unit] =
      Applicative[F].whenA(cleanUp) {
        F.delay {
          Orchestration.workspaces.delete(cloneBillingProject, workspaceName)(creatorAuthToken)
        }
      }

    Resource.make(cloneWorkspace)(destroyWorkspace)
  }
}
