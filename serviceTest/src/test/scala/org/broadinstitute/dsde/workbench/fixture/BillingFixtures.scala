package org.broadinstitute.dsde.workbench.fixture

import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Sync}
import cats.implicits.{catsSyntaxApply, toFoldableOps}
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.BillingProject.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.BillingProject.BillingProjectRole.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.Orchestration

import java.util.UUID

object BillingFixtures {

  /**
   * Create a new v2 billing project for the activation of `testCode`. The billing project will be
   * destroyed when control exits `testCode`.
   *
   * @param billingAccountName Name of Google billing account the new billing project will bill to.
   * @param projectNamePrefix  Prefix for billing project name. [default: "tmp-billing-project-"]
   * @param owners             List of billing project owner email addresses. [default: empty]
   * @param users              List of billing project user email addresses [default: empty]
   * @param testCode           Code to exercise with new billing project
   * @param creatorAuthToken   Auth token of billing project creator
   */
  def withCleanBillingProject[A](billingAccountName: String,
                                 projectNamePrefix: String = "tmp-billing-project-",
                                 owners: List[String] = List.empty,
                                 users: List[String] = List.empty
  )(testCode: String => A)(implicit creatorAuthToken: AuthToken): A =
    withCleanBillingProjectF(billingAccountName, projectNamePrefix, owners, users) { billingProject =>
      IO(testCode(billingProject))
    }.unsafeRunSync

  /**
   * Create a new v2 billing project for the activation of `testCode` supporting suspension of
   * side-effects within some `Sync[F]`. The billing project will be destroyed after the effects of
   * `testCode` are sequenced.
   *
   * @param billingAccountName Name of Google billing account the new billing project will bill to.
   * @param projectNamePrefix  Prefix for billing project name. [default: "tmp-billing-project-"]
   * @param owners             List of billing project owner email addresses. [default: empty]
   * @param users              List of billing project user email addresses [default: empty]
   * @param testCode           Code to exercise with new billing project
   * @param creatorAuthToken   Auth token of billing project creator
   */
  def withCleanBillingProjectF[F[_], A](billingAccountName: String,
                                        projectNamePrefix: String = "tmp-billing-project-",
                                        owners: List[String] = List.empty,
                                        users: List[String] = List.empty
  )(testCode: String => F[A])(implicit creatorAuthToken: AuthToken, F: Sync[F]): F[A] = {

    def addMembers(projectName: String, emails: List[String], role: BillingProjectRole): F[Unit] =
      emails.traverse_ { email =>
        F.delay(Orchestration.billingV2.addUserToBillingProject(projectName, email, role))
      }

    BillingFixtures
      .temporaryBillingProject(billingAccountName, projectNamePrefix)
      .use { projectName =>
        addMembers(projectName, owners, BillingProjectRole.Owner) *>
          addMembers(projectName, users, BillingProjectRole.User) *>
          testCode(projectName)
      }
  }

  /**
   * Create a v2 billing project `Resource`.
   *
   * @param billingAccountName Name of Google billing account the new billing project will bill to.
   * @param projectNamePrefix  Prefix for billing project name. [default: "tmp-billing-project-"]
   * @param creatorAuthToken   Auth token of billing project creator
   */
  def temporaryBillingProject[F[_]](billingAccountName: String, projectNamePrefix: String = "tmp-billing-project-")(
    implicit
    creatorAuthToken: AuthToken,
    F: Sync[F]
  ): Resource[F, String] = {

    def createBillingProject: F[String] = F.delay {
      val projectName = projectNamePrefix
        .++(UUID.randomUUID.toString.replace("-", ""))
        .substring(0, 30)

      Orchestration.billingV2.createBillingProject(projectName, billingAccountName)
      projectName
    }

    def destroyBillingProject(projectName: String): F[Unit] = F.unit <* F.delay {
      Orchestration.billingV2.deleteBillingProject(projectName)
    }

    Resource.make(createBillingProject)(destroyBillingProject)
  }
}
