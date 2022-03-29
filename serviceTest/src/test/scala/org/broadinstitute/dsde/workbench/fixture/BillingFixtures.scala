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
   * @param projectNamePrefix  Prefix for billing project name.                  [default: "tmp-billing-project-"]
   * @param owners             Additional billing project owner email addresses. [default: None]
   * @param users              Additional billing project user email addresses   [default: None]
   * @param testCode           Code to exercise with new billing project
   * @param creatorAuthToken   Auth token of billing project creator
   */
  def withTemporaryBillingProject[A](billingAccountName: String,
                                     projectNamePrefix: Option[String] = None,
                                     owners: Option[List[String]] = None,
                                     users: Option[List[String]] = None
  )(testCode: String => A)(implicit creatorAuthToken: AuthToken): A =
    BillingFixtures
      .temporaryBillingProject[IO](billingAccountName, creatorAuthToken, projectNamePrefix, owners, users)
      .use(projectName => IO.delay(testCode(projectName)))
      .unsafeRunSync

  /**
   * Create a v2 billing project `Resource`.
   *
   * @param billingAccountName Name of Google billing account the new billing project will bill to.
   * @param projectNamePrefix  Prefix for billing project name. [default: "tmp-billing-project-"]
   * @param creatorAuthToken   Auth token of billing project creator
   */

  /**
   * Create a v2 billing project `Resource` whose lifetime is bound to the `Resource`'s `use` method.
   *
   * @param billingAccountName Name of Google billing account the new billing project will bill to.
   * @param creatorAuthToken   Auth token of billing project creator
   * @param projectNamePrefix  Prefix for billing project name.                  [default: "tmp-billing-project-"]
   * @param owners             Additional billing project owner email addresses. [default: None]
   * @param users              Additional billing project user email addresses   [default: None]
   * @return
   */
  def temporaryBillingProject[F[_]](billingAccountName: String,
                                    creatorAuthToken: AuthToken,
                                    projectNamePrefix: Option[String] = None,
                                    owners: Option[List[String]] = None,
                                    users: Option[List[String]] = None
  )(implicit F: Sync[F]): Resource[F, String] = {
    def createBillingProject: F[String] = F.delay {
      val projectName = projectNamePrefix
        .getOrElse("tmp-billing-project-")
        .++(UUID.randomUUID.toString.replace("-", ""))
        .substring(0, 30)

      Orchestration.billingV2.createBillingProject(projectName, billingAccountName)(creatorAuthToken)
      projectName
    }

    def destroyBillingProject(projectName: String): F[Unit] = F.unit <* F.delay {
      Orchestration.billingV2.deleteBillingProject(projectName)(creatorAuthToken)
    }

    def addMembers(projectName: String, emails: List[String], role: BillingProjectRole): Resource[F, Unit] =
      emails.traverse_ { email =>
        Resource.eval(F.delay {
          Orchestration.billingV2.addUserToBillingProject(projectName, email, role)(creatorAuthToken)
        })
      }

    for {
      billingProject <- Resource.make(createBillingProject)(destroyBillingProject)
      _ <- addMembers(billingProject, owners.getOrElse(List.empty), BillingProjectRole.Owner)
      _ <- addMembers(billingProject, users.getOrElse(List.empty), BillingProjectRole.User)
    } yield billingProject
  }
}
