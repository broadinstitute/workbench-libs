package org.broadinstitute.dsde.workbench.fixture

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.service.{GPAlloc, Orchestration, Rawls}
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.test.CleanUp
import org.scalatest.TestSuite

import scala.util.Random

trait BillingFixtures extends CleanUp {
  self: TestSuite =>

  // copied from WebBrowserSpec so we don't have to self-type it
  // TODO make it common to API and browser tests
  private def makeRandomId(length: Int = 7): String = {
    Random.alphanumeric.take(length).mkString.toLowerCase
  }

  protected def addMembersToBillingProject(projectName: String, memberEmails: List[String])(implicit token: AuthToken): Unit = {
    memberEmails foreach { email =>
      Orchestration.billing.addUserToBillingProject(projectName, email, BillingProjectRole.Owner)
    }
  }

  protected def removeMembersFromBillingProject(projectName: String, memberEmails: List[String])(implicit token: AuthToken): Unit = {
    memberEmails foreach { email =>
      try {
        Orchestration.billing.removeUserFromBillingProject(projectName, email, BillingProjectRole.Owner)
      } catch nonFatalAndLog(s"Error removing user $email from billing project $projectName in removeMembersFromBillingProject clean-up")
    }
  }

  @deprecated(message = "withBillingProject is deprecated. Use withCleanBillingProject if you want a billing project to isolate your tests, or withBrandNewBillingProject if you want to create a brand new one",
              since="workbench-service-test-0.5")
  def withBillingProject(namePrefix: String, memberEmails: List[String] = List())
                        (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    withBrandNewBillingProject(namePrefix, memberEmails)(testCode)(token)
  }

  def withBrandNewBillingProject(namePrefix: String, memberEmails: List[String] = List())
                                (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val billingProjectName = namePrefix + "-" + makeRandomId()
    Orchestration.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)
    addMembersToBillingProject(billingProjectName, memberEmails)
    try {
      testCode(billingProjectName)
    } finally {
      removeMembersFromBillingProject(billingProjectName, memberEmails)
      try {
        Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())
      } catch nonFatalAndLog(s"Error deleting billing project in withBillingProject clean-up: $billingProjectName")
    }
  }

  def withCleanBillingProject(newOwnerCreds: Credentials, memberEmails: List[String] = List())(testCode: (String) => Any): Unit = {
    //request a GPAlloced project as the potential new owner
    val newOwnerToken = newOwnerCreds.makeAuthToken()
    GPAlloc.projects.requestProject(newOwnerToken) match {
      case Some(project) =>
        //call the Rawls endpoint to register a precreated project needs to be called by a Rawls admin
        val adminToken = UserPool.chooseAdmin.makeAuthToken()
        Rawls.admin.claimProject(project.projectName, project.cromwellAuthBucketUrl, newOwnerCreds.email)(adminToken)

        addMembersToBillingProject(project.projectName, memberEmails)(newOwnerToken)

        try {
          testCode(project.projectName)
        } finally {
          removeMembersFromBillingProject(project.projectName, memberEmails)(newOwnerToken)

          try {
            Rawls.admin.releaseProject(project.projectName)(adminToken)
          } catch nonFatalAndLog(s"Error releasing billing project from Rawls in withCleanBillingProject clean-up: ${project.projectName}")

          try {
            GPAlloc.projects.releaseProject(project.projectName)(newOwnerToken)
          } catch nonFatalAndLog(s"Error releasing billing project from GPAlloc in withCleanBillingProject clean-up: ${project.projectName}")
        }

      case None =>
        logger.warn("withCleanBillingProject got no project back from GPAlloc. Falling back to making a brand new one...")
        withBrandNewBillingProject("billingproj")(testCode)(newOwnerToken)
    }
  }

  def addUserInBillingProject(billingProjectName: String, email: String, role: BillingProjectRole)
                             (implicit token: AuthToken): Unit = {
    Orchestration.billing.addUserToBillingProject(billingProjectName, email, role)
    register cleanUp Orchestration.billing.removeUserFromBillingProject(billingProjectName, email, role)
  }
}

