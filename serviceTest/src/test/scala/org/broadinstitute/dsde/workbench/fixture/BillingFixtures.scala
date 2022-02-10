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

/**
 * Mix in this trait to allow your test to access billing projects managed by the GPAlloc system, or create new
 * billing projects of your own.  Using GPAlloc will generally be much faster, limit the creation of billing projects
 * to those tests which truly require them.
 */
trait BillingFixtures {

  /**
   * Create a new v2 billing project for the activation of `testCode`. The billing project will be
   * destroyed when control exists `testCode`.
   *
   * @param billingAccountName Name of Google billing account the new billing project will bill to.
   * @param projectNamePrefix  Prefix for billing project name. [default: "tmp-billing-project-"]
   * @param ownerEmails        List of billing project owner email addresses. [default: empty]
   * @param userEmails         List of billing project user email addresses [default: empty]
   * @param testCode           Code to exercise with new billing project
   * @param ownerAuthToken     Auth token of billing project creator
   */
  def withCleanBillingProject[A](billingAccountName: String,
                                 projectNamePrefix: String = "tmp-billing-project-",
                                 ownerEmails: List[String] = List.empty,
                                 userEmails: List[String] = List.empty
  )(testCode: String => A)(implicit ownerAuthToken: AuthToken): A =
    withCleanBillingProjectF(billingAccountName, projectNamePrefix, ownerEmails, userEmails) { billingProject =>
      IO(testCode(billingProject))
    }.unsafeRunSync

  /**
   * Create a new v2 billing project for the activation of `testCode` supporting suspension of
   * side-effects within some `Sync[F]`. The billing project will be destroyed after the effects of
   * `testCode` are sequenced.
   *
   * @param billingAccountName Name of Google billing account the new billing project will bill to.
   * @param projectNamePrefix  Prefix for billing project name. [default: "tmp-billing-project-"]
   * @param ownerEmails        List of billing project owner email addresses. [default: empty]
   * @param userEmails         List of billing project user email addresses [default: empty]
   * @param testCode           Code to exercise with new billing project
   * @param ownerAuthToken     Auth token of billing project creator
   */
  def withCleanBillingProjectF[F[_], A](billingAccountName: String,
                                        projectNamePrefix: String = "tmp-billing-project-",
                                        ownerEmails: List[String] = List.empty,
                                        userEmails: List[String] = List.empty
  )(testCode: String => F[A])(implicit ownerAuthToken: AuthToken, F: Sync[F]): F[A] = {
    def createProject: F[String] = F.delay {
      val projectName = projectNamePrefix ++ UUID.randomUUID.toString.replace("-", "")
      Orchestration.billingV2.createBillingProject(projectName, billingAccountName)
      projectName
    }

    def destroyProject(projectName: String): F[Unit] =
      F.delay(Orchestration.billingV2.deleteBillingProject(projectName)) *> F.unit

    def addMembers(projectName: String, emails: List[String], role: BillingProjectRole): F[Unit] =
      emails.traverse_ { email =>
        F.delay(Orchestration.billingV2.addUserToBillingProject(projectName, email, role))
      }

    Resource.make(createProject)(destroyProject).use { projectName =>
      addMembers(projectName, ownerEmails, BillingProjectRole.Owner) *>
        addMembers(projectName, userEmails, BillingProjectRole.User) *>
        testCode(projectName)
    }
  }
}
