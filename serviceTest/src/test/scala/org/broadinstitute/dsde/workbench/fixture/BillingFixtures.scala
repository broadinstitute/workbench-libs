package org.broadinstitute.dsde.workbench.fixture

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Sync}
import cats.implicits.{catsSyntaxApply, toFoldableOps}
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.rawls.model.AzureManagedAppCoordinates
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.BillingProject.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.BillingProject.BillingProjectRole.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.util.Retry
import org.broadinstitute.dsde.workbench.service.{Orchestration, RestException}

import java.util.UUID
import scala.concurrent.duration.DurationInt

object BillingFixtures extends LazyLogging {

  /**
   * Create a new v2 billing project for the activation of `testCode`. The billing project will be
   * destroyed when control exits `testCode`.
   *
   * @param billingAccountId Id of Google billing account the billing project will bill to in the
   *                         form "billingAccounts/000000-000000-000000".
   * @param prefix           Prefix for billing project name.                  [default: "tmp-billing-project-"]
   * @param owners           Additional billing project owner email addresses. [default: None]
   * @param users            Additional billing project user email addresses   [default: None]
   * @param testCode         Code to exercise with new billing project
   * @param creatorAuthToken Auth token of billing project creator
   */
  def withTemporaryBillingProject[A](billingAccountId: String,
                                     prefix: Option[String] = None,
                                     owners: Option[List[String]] = None,
                                     users: Option[List[String]] = None
  )(testCode: String => A)(implicit creatorAuthToken: AuthToken): A =
    BillingFixtures
      .temporaryBillingProject[IO](Left(billingAccountId), creatorAuthToken, prefix, owners, users)
      .use(projectName => IO.delay(testCode(projectName)))
      .unsafeRunSync

  /**
   * Creates a new v2 AZURE billing project for the activation of `testCode`. This billing project will be destroyed
   * when control exits `testCode`.
   *
   * @param azureManagedAppCoordinates Azure MRG coordinates pointing to a deployment of the Terra managed application.
   * @param prefix                     Prefix for billing project name.                  [default: "tmp-billing-project-"]
   * @param owners                     Additional billing project owner email addresses. [default: None]
   * @param users                      Additional billing project user email addresses   [default: None]
   * @param testCode                   Code to exercise with new billing project
   * @param creatorAuthToken           Auth token of billing project creator
   */
  def withTemporaryAzureBillingProject[A](azureManagedAppCoordinates: AzureManagedAppCoordinates,
                                          prefix: Option[String] = None,
                                          owners: Option[List[String]] = None,
                                          users: Option[List[String]] = None
  )(testCode: String => A)(implicit creatorAuthToken: AuthToken): A =
    BillingFixtures
      .temporaryBillingProject[IO](Right(azureManagedAppCoordinates), creatorAuthToken, prefix, owners, users)
      .use(projectName => IO.delay(testCode(projectName)))
      .unsafeRunSync

  /**
   * Create a v2 billing project `Resource` whose lifetime is bound to the `Resource`'s `use` method.
   *
   * @param billingInformation Id of Google billing account the billing project will bill to in the
   *                           form "billingAccounts/000000-000000-000000"
   *                           **OR**
   *                           managed app "coordinates" pointing at a deployment of the Terra managed application
   *                           on Azure.
   * @param creatorAuthToken   Auth token of billing project creator
   * @param prefix             Prefix for billing project name.                  [default: "tmp-billing-project-"]
   * @param owners             Additional billing project owner email addresses. [default: None]
   * @param users              Additional billing project user email addresses   [default: None]
   */
  def temporaryBillingProject[F[_]](billingInformation: Either[String, AzureManagedAppCoordinates],
                                    creatorAuthToken: AuthToken,
                                    prefix: Option[String] = None,
                                    owners: Option[List[String]] = None,
                                    users: Option[List[String]] = None
  )(implicit F: Sync[F]): Resource[F, String] = {
    def createBillingProject: F[String] = F.delay {
      val projectName = prefix
        .getOrElse("tmp-billing-project-")
        .++(UUID.randomUUID.toString.replace("-", ""))
        .substring(0, 30)

      Orchestration.billingV2.createBillingProject(projectName, billingInformation)(creatorAuthToken)

      if (
        Retry.retryWithPredicate(1.seconds, 1.minutes) {
          isBillingProjectReady(projectName, creatorAuthToken)
        }
      ) {
        logger.info(s"Billing project ${projectName} created.")
        projectName
      } else {
        throw new Exception(s"Error creating billing project ${projectName}")
      }
    }

    def destroyBillingProject(projectName: String): F[Unit] = F.unit <* F.delay {
      Orchestration.billingV2.deleteBillingProject(projectName)(creatorAuthToken)
      if (
        Retry.retryWithPredicate(1.seconds, 1.minutes) {
          isBillingProjectDeleted(projectName, creatorAuthToken)
        }
      ) {
        true
      } else {
        throw new Exception("Error deleting billing project")
      }
    }

    def addMembers(projectName: String, emails: List[String], role: BillingProjectRole): Resource[F, Unit] =
      emails.traverse_ { email =>
        Resource.eval(F.delay {
          Orchestration.billingV2.addUserToBillingProject(projectName, email, role)(creatorAuthToken)
        })
      }

    def isBillingProjectDeleted(projectName: String, authToken: AuthToken): Boolean =
      try {
        logger.info(s"Checking deletion status of billing project ${projectName}...")
        Orchestration.billingV2.getBillingProject(projectName)(authToken)
        false
      } catch {
        case e: RestException =>
          if (e.statusCode == StatusCodes.NotFound) {
            logger.info(s"Billing project ${projectName} deleted.")
            true
          } else {
            throw new Exception(s"Error deleting billing project ${projectName}")
          }
      }

    def isBillingProjectReady(projectName: String, authToken: AuthToken): Boolean = {
      logger.info(s"Checking creation status of billing project ${projectName}...")
      val bp = Orchestration.billingV2.getBillingProject(projectName)(authToken)
      bp("status").equalsIgnoreCase("ready")
    }

    for {
      billingProject <- Resource.make(createBillingProject)(destroyBillingProject)
      _ <- addMembers(billingProject, owners.getOrElse(List.empty), BillingProjectRole.Owner)
      _ <- addMembers(billingProject, users.getOrElse(List.empty), BillingProjectRole.User)
    } yield billingProject
  }
}
