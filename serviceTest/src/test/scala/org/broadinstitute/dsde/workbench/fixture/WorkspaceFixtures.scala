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
   * @param billingProjectName Billing project (or namespace) for workspace.
   * @param prefix             Prefix for workspace name.                    [default "tmp-workspace-"]
   * @param authDomain         Set of members group names for auth domain.   [default None]
   * @param acl                Additional access Control List entries.       [default None]
   * @param attributes         Additional workspace attributes.              [default None]
   * @param bucketLocation     Region where the workspace should be created. [default None]
   * @param cleanUp            Delete the workspace after use.               [default true]
   * @param testCode           Test code to run
   * @param token              Auth token of workspace creator
   */
  def withTemporaryWorkspace[A](billingProjectName: String,
                                prefix: Option[String] = None,
                                authDomain: Option[Set[String]] = None,
                                acl: Option[List[AclEntry]] = None,
                                attributes: Option[Map[String, Any]] = None,
                                bucketLocation: Option[String] = None,
                                cleanUp: Boolean = true
  )(testCode: (String) => A)(implicit token: AuthToken): A =
    WorkspaceFixtures
      .temporaryWorkspace[IO](billingProjectName, token, prefix, authDomain, acl, attributes, bucketLocation, cleanUp)
      .use(workspaceName => IO.delay(testCode(workspaceName)))
      .unsafeRunSync

  /**
   * Loan method that creates a workspace and then clones it. Both workspaces will be cleaned
   * up after the test code is run. The workspace names will contain a random and highly
   * likely unique series of characters.
   *
   * @param billingProjectName Billing project (or namespace) for workspace.
   * @param prefix             Prefix for workspace name.                    [default "tmp-workspace-"]
   * @param authDomain         Set of members group names for auth domain.   [default None]
   * @param acl                Additional access Control List entries.       [default None]
   * @param attributes         Additional workspace attributes.              [default None]
   * @param bucketLocation     Region where the workspace should be created. [default None]
   * @param cleanUp            Delete the workspace after use.               [default true]
   * @param testCode           Test code to run
   * @param token              Auth token of workspace creator
   */
  def withTemporaryWorkspaceClone[A](billingProjectName: String,
                                     prefix: Option[String] = None,
                                     authDomain: Option[Set[String]] = None,
                                     acl: Option[List[AclEntry]] = None,
                                     attributes: Option[Map[String, Any]] = None,
                                     bucketLocation: Option[String] = None,
                                     cleanUp: Boolean = true
  )(testCode: (String) => A)(implicit token: AuthToken): A = {
    val temporaryClone = for {
      workspaceName <- WorkspaceFixtures.temporaryWorkspace[IO](
        billingProjectName,
        token,
        prefix = prefix,
        authDomain = authDomain,
        acl = acl,
        attributes = attributes,
        bucketLocation = bucketLocation,
        cleanUp = cleanUp
      )

      clone <- WorkspaceFixtures.temporaryWorkspaceClone[IO](
        workspaceBillingProject = billingProjectName,
        workspaceName = workspaceName,
        cloneBillingProject = billingProjectName,
        creatorAuthToken = token,
        prefix = prefix.map(_ + "-clone-"),
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
   * @param prefix             Prefix for workspace name.                    [default "tmp-workspace-"]
   * @param authDomain         Set of members group names for auth domain.   [default None]
   * @param acl                Additional access Control List entries.       [default None]
   * @param attributes         Optional workspace attributes.                [default None]
   * @param bucketLocation     Region where the workspace should be created. [default None]
   * @param cleanUp            Delete the workspace after use.               [default true]
   */
  def temporaryWorkspace[F[_]](billingProjectName: String,
                               creatorAuthToken: AuthToken,
                               prefix: Option[String] = None,
                               authDomain: Option[Set[String]] = None,
                               acl: Option[List[AclEntry]] = None,
                               attributes: Option[Map[String, Any]] = None,
                               bucketLocation: Option[String] = None,
                               cleanUp: Boolean = true
  )(implicit F: Sync[F]): Resource[F, String] = {
    def createWorkspace: F[String] = F.delay {
      val workspaceName = randomIdWithPrefix(prefix.getOrElse("tmp-workspace-"))
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
      _ <- acl.traverse_ { entries =>
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
   * @param prefix                  Prefix for clone name.                       [default workspaceName + "-clone-"]
   * @param authDomain              Set of members group names for auth domain.  [default None]
   * @param cleanUp                 Delete the clone after use.                  [default true]
   */
  def temporaryWorkspaceClone[F[_]](workspaceBillingProject: String,
                                    workspaceName: String,
                                    cloneBillingProject: String,
                                    creatorAuthToken: AuthToken,
                                    prefix: Option[String] = None,
                                    authDomain: Option[Set[String]] = None,
                                    cleanUp: Boolean = true
  )(implicit F: Sync[F]): Resource[F, String] = {
    def cloneWorkspace: F[String] = F.delay {
      val cloneName = randomIdWithPrefix(prefix.getOrElse(workspaceName + "-clone-"))
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
