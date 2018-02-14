package org.broadinstitute.dsde.workbench.fixture
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.{Orchestration, Rawls}
import org.broadinstitute.dsde.workbench.service.test.CleanUp
import org.scalatest.TestSuite

import scala.util.Random

trait BillingFixtures extends CleanUp { self: TestSuite =>

  // copied from WebBrowserSpec so we don't have to self-type it
  // TODO make it common to API and browser tests
  private def makeRandomId(length: Int = 7): String = {
    Random.alphanumeric.take(length).mkString.toLowerCase
  }

  def withBillingProject(namePrefix: String)
                        (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val billingProjectName = namePrefix + "-" + makeRandomId()
    Orchestration.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)
    try {
      testCode(billingProjectName)
    } finally {
      try {
        Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())
      } catch nonFatalAndLog(s"Error deleting billing project in withBillingProject clean-up: $billingProjectName")
    }
  }

  def addUserInBillingProject(billingProjectName: String, email: String, role: BillingProjectRole)(implicit token: AuthToken): Unit = {
    try {
      Orchestration.billing.addUserToBillingProject(billingProjectName, email, role)
      register cleanUp Orchestration.billing.removeUserFromBillingProject(billingProjectName, email, role)
    } catch nonFatalAndLog(s"Error removing $email from $billingProjectName in addUserInBillingProject cleanup")
  }

}

