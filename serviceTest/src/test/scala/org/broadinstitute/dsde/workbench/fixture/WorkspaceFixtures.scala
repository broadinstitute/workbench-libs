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
   * @param billingProject Billing project (or namespace) for workspace.
   * @param prefix         Prefix for workspace name.                    [default "tmp-workspace-"]
   * @param authDomain     Set of members group names for auth domain.   [default None]
   * @param acl            Additional access Control List entries.       [default None]
   * @param attributes     Additional workspace attributes.              [default None]
   * @param bucketLocation Region where the workspace should be created. [default None]
   * @param cleanUp        Delete the workspace after use.               [default true]
   * @param testCode       Test code to run
   * @param token          Auth token of workspace creator
   */
  def withTemporaryWorkspace[A](billingProject: String,
                                prefix: Option[String] = None,
                                authDomain: Option[Set[String]] = None,
                                acl: Option[List[AclEntry]] = None,
                                attributes: Option[Map[String, Any]] = None,
                                bucketLocation: Option[String] = None,
                                cleanUp: Boolean = true
                               )(testCode: (String) => A)(implicit token: AuthToken): A =
    WorkspaceFixtures
      .temporaryWorkspace[IO](billingProject, token, prefix, authDomain, acl, attributes, bucketLocation, cleanUp)
      .use(workspaceName => IO.delay(testCode(workspaceName)))
      .unsafeRunSync

  /**
   * Loan method that creates a temporary clone of a workspace. The cloned workspace will be cleaned
   * up after the test code is run. The workspace name will contain a random and highly
   * likely unique series of characters.
   *
   * @param workspaceBillingProject Billing project (or namespace) of workspace to clone.
   * @param workspaceName           Name of workspace to clone.
   * @param cloneBillingProject     Billing project (or namespace) for clone.
   * @param prefix                  Prefix for clone name.                       [default workspaceName + "-clone-"]
   * @param authDomain              Set of members group names for auth domain.  [default None]
   * @param acl                     Additional access Control List entries.      [default None]
   * @param attributes              Optional workspace attributes.               [default None]
   * @param cleanUp                 Delete the clone after use.                  [default true]
   * @param testCode                Test code to run
   * @param token                   Auth token of workspace creator
   */
  def withTemporaryWorkspaceClone[A](workspaceBillingProject: String,
                                     workspaceName: String,
                                     cloneBillingProject: String,
                                     prefix: Option[String] = None,
                                     authDomain: Option[Set[String]] = None,
                                     acl: Option[List[AclEntry]] = None,
                                     attributes: Option[Map[String, Any]] = None,
                                     cleanUp: Boolean = true
                                    )(testCode: (String) => A)(implicit token: AuthToken): A =
    WorkspaceFixtures
      .temporaryWorkspaceClone[IO](
        workspaceBillingProject,
        workspaceName,
        cloneBillingProject,
        creatorAuthToken = token,
        prefix = prefix.map(_ + "-clone-"),
        authDomain = authDomain,
        acl = acl,
        attributes = attributes,
        cleanUp = cleanUp
      )
      .use(clonedWorkspaceName => IO.delay(testCode(clonedWorkspaceName)))
      .unsafeRunSync

  /**
   * Create a temporary workspace `Resource` whose lifetime is bound to the `Resource`'s `use` method.
   *
   * @param billingProject   Billing project (or namespace) for workspace.
   * @param creatorAuthToken Access token of the workspace creator.
   * @param prefix           Prefix for workspace name.                    [default "tmp-workspace-"]
   * @param authDomain       Set of members group names for auth domain.   [default None]
   * @param acl              Additional access Control List entries.       [default None]
   * @param attributes       Optional workspace attributes.                [default None]
   * @param bucketLocation   Region where the workspace should be created. [default None]
   * @param cleanUp          Delete the workspace after use.               [default true]
   */
  def temporaryWorkspace[F[_]](billingProject: String,
                               creatorAuthToken: AuthToken,
                               prefix: Option[String] = None,
                               authDomain: Option[Set[String]] = None,
                               acl: Option[List[AclEntry]] = None,
                               attributes: Option[Map[String, Any]] = None,
                               bucketLocation: Option[String] = None,
                               cleanUp: Boolean = true
                              )(implicit F: Sync[F]): Resource[F, String] =
    makeWorkspaceResource(
      createWorkspace = F.delay {
        val workspaceName = randomIdWithPrefix(prefix.getOrElse("tmp-workspace-"))
        Orchestration.workspaces.create(
          billingProject,
          workspaceName,
          authDomain.getOrElse(Set.empty),
          bucketLocation
        )(creatorAuthToken)
        workspaceName
      },
      billingProject,
      creatorAuthToken,
      acl,
      attributes,
      cleanUp
    )

  /**
   * Create a temporary workspace clone `Resource` whose lifetime is bound to the `Resource`'s `use` method.
   *
   * @param workspaceBillingProject Billing project (or namespace) of workspace to clone.
   * @param workspaceName           Name of workspace to clone.
   * @param cloneBillingProject     Billing project (or namespace) for clone.
   * @param creatorAuthToken        Access token of the workspace creator
   * @param prefix                  Prefix for clone name.                       [default workspaceName + "-clone-"]
   * @param authDomain              Set of members group names for auth domain.  [default None]
   * @param acl                     Additional access Control List entries.      [default None]
   * @param attributes              Optional workspace attributes.               [default None]
   * @param cleanUp                 Delete the clone after use.                  [default true]
   */
  def temporaryWorkspaceClone[F[_]](workspaceBillingProject: String,
                                    workspaceName: String,
                                    cloneBillingProject: String,
                                    creatorAuthToken: AuthToken,
                                    prefix: Option[String] = None,
                                    authDomain: Option[Set[String]] = None,
                                    acl: Option[List[AclEntry]] = None,
                                    attributes: Option[Map[String, Any]] = None,
                                    cleanUp: Boolean = true
                                   )(implicit F: Sync[F]): Resource[F, String] =
    makeWorkspaceResource(
      createWorkspace = F.delay {
        val cloneName = randomIdWithPrefix(prefix.getOrElse(workspaceName + "-clone-"))
        Orchestration.workspaces.clone(
          workspaceBillingProject,
          workspaceName,
          cloneBillingProject,
          cloneName,
          authDomain.getOrElse(Set.empty)
        )(creatorAuthToken)
        cloneName
      },
      cloneBillingProject,
      creatorAuthToken,
      acl,
      attributes,
      cleanUp
    )

  private def makeWorkspaceResource[F[_]](createWorkspace: F[String],
                                          billingProject: String,
                                          token: AuthToken,
                                          acl: Option[List[AclEntry]],
                                          attributes: Option[Map[String, Any]],
                                          cleanUp: Boolean
                                         )(implicit F: Sync[F]): Resource[F, String] = {
    def destroyWorkspace(workspaceName: String) =
      Applicative[F].whenA(cleanUp) {
        F.delay {
          Orchestration.workspaces.delete(billingProject, workspaceName)(token)
        }
      }

    def updateWorkspaceAcl(workspaceName: String, entries: List[AclEntry]) =
      Resource.eval(F.delay {
        Orchestration.workspaces.updateAcl(billingProject, workspaceName, entries)(token)
      })

    def updateWorkspaceAttributes(workspaceName: String, attributes: Map[String, Any]) =
      Resource.eval(F.delay {
        Orchestration.workspaces.setAttributes(billingProject, workspaceName, attributes)(token)
      })

    for {
      workspaceName <- Resource.make(createWorkspace)(destroyWorkspace)
      _ <- acl.traverse_(updateWorkspaceAcl(workspaceName, _))
      _ <- attributes.traverse_(updateWorkspaceAttributes(workspaceName, _))
    } yield workspaceName
  }

}
