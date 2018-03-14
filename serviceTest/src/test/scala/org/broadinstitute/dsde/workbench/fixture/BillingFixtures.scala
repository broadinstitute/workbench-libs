package org.broadinstitute.dsde.workbench.fixture

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.model.{UserInfo, WorkbenchEmail, WorkbenchUserId}
import org.broadinstitute.dsde.workbench.service.{GPAlloc, Orchestration, Rawls}
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.test.CleanUp
import org.scalatest.TestSuite

import scala.util.Random


/**
  * Mix in this trait to allow your test to access billing projects managed by the GPAlloc system, or create new
  * billing projects of your own.  Using GPAlloc will generally be much faster, limit the creation of billing projects
  * to those tests which truly require them.
  */
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
      Orchestration.billing.removeUserFromBillingProject(projectName, email, BillingProjectRole.Owner)
    }
  }

  @deprecated(message = "withBillingProject is deprecated. Use withCleanBillingProject if you want a billing project to isolate your tests, or withBrandNewBillingProject if you want to create a brand new one",
              since="workbench-service-test-0.5")
  def withBillingProject(namePrefix: String, memberEmails: List[String] = List())
                        (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    withBrandNewBillingProject(namePrefix, memberEmails)(testCode)(token)
  }

  case class ClaimedProject(projectName: String, gpAlloced: Boolean) {
    def cleanup(ownerCreds: Credentials, memberEmails: List[String]): Unit = {
      if (gpAlloced)
        releaseGPAllocProject(projectName, ownerCreds, memberEmails)
      else {
        deleteBillingProject(projectName, memberEmails)(ownerCreds.makeAuthToken())
      }
    }
  }

  private def createNewBillingProject(namePrefix: String, memberEmails: List[String] = List())(implicit token: AuthToken): String = {
    val billingProjectName = namePrefix + "-" + makeRandomId()
    Orchestration.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)
    addMembersToBillingProject(billingProjectName, memberEmails)
    billingProjectName
  }

  private def deleteBillingProject(billingProjectName: String, memberEmails: List[String])(implicit token: AuthToken): Unit = {
    removeMembersFromBillingProject(billingProjectName, memberEmails)
    Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())
  }

  /**
    * Create and use a new billing project.  Keep in mind that this is a slow an error-prone process, so you should only
    * use this method if your test requires it.
    *
    * @param namePrefix a short String to use as a billing project name prefix for identifying your test.
    * @param memberEmails a List of member emails (as Strings) to add as members of this project
    * @param testCode your test
    * @param token an AuthToken representing a billing project owner to pass to billing project endpoints
    */
  def withBrandNewBillingProject(namePrefix: String, memberEmails: List[String] = List())
                                (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val billingProjectName = createNewBillingProject(namePrefix, memberEmails)
    try {
      testCode(billingProjectName)
    } finally {
      deleteBillingProject(billingProjectName, memberEmails)
    }
  }

  /**
    * Manually claim a project provisioned by GPAlloc and optionally add members.
    * Consider using `withCleanBillingProject()` instead if you don't need to control the use of projects.
    *
    * @param newOwnerCreds Credentials representing a billing project owner to pass to billing project endpoints
    * @param memberEmails a List of member emails (as Strings) to add as members of this project
    * @return Some(GPAllocProject) if it succeeded, None if it did not
    */
  def claimGPAllocProject(newOwnerCreds: Credentials, memberEmails: List[String] = List()): ClaimedProject = {
    //request a GPAlloced project as the potential new owner
    val newOwnerToken = newOwnerCreds.makeAuthToken()
    GPAlloc.projects.requestProject(newOwnerToken) match {
      case Some(project) =>
        //the Rawls endpoint to register a precreated project needs to be called by a Rawls admin
        //but it also takes the new owner's UserInfo in order to create the resource as them in Sam
        val adminToken = UserPool.chooseAdmin.makeAuthToken()
        val newOwnerUserInfo = UserInfo(OAuth2BearerToken(newOwnerToken.value), WorkbenchUserId("0"), WorkbenchEmail(newOwnerCreds.email), 3600)
        Rawls.admin.claimProject(project.projectName, project.cromwellAuthBucketUrl, newOwnerUserInfo)(adminToken)

        addMembersToBillingProject(project.projectName, memberEmails)(newOwnerToken)

        ClaimedProject(project.projectName, gpAlloced = true)
      case _ =>
        logger.warn("claimGPAllocProject got no project back from GPAlloc. Falling back to making a brand new one...")
        val billingProjectName = createNewBillingProject("billingproj", memberEmails)(newOwnerToken)
        ClaimedProject(billingProjectName, gpAlloced = false)
    }
  }

  /**
    * Release a billing project back to GPAlloc when you are done with it.
    * Consider using `withCleanBillingProject()` instead if you don't need to control the use of projects.
    *
    * @param projectName the GPAllocProject to release
    * @param ownerCreds the Credentials of the current owner of the project
    * @param memberEmails a List of member emails (as Strings) to remove as project members
    */
  def releaseGPAllocProject(projectName: String, ownerCreds: Credentials, memberEmails: List[String] = List()): Unit = {
    val ownerToken = ownerCreds.makeAuthToken()
    val adminToken = UserPool.chooseAdmin.makeAuthToken()
    val newOwnerUserInfo = UserInfo(OAuth2BearerToken(ownerToken.value), WorkbenchUserId("0"), WorkbenchEmail(ownerCreds.email), 3600)

    removeMembersFromBillingProject(projectName, memberEmails)(ownerToken)

    Rawls.admin.releaseProject(projectName, newOwnerUserInfo)(adminToken)

    GPAlloc.projects.releaseProject(projectName)(ownerToken)
  }

  /**
    * Use a billing project provided by GPAlloc for the purpose of running tests against it.  This method will claim
    * a project for the duration of the test and release it when the test is done.
    *
    * @param newOwnerCreds Credentials representing a billing project owner to pass to billing project endpoints
    * @param memberEmails a List of member emails (as Strings) to add as members of this project
    * @param testCode your test
    */
  def withCleanBillingProject(newOwnerCreds: Credentials, memberEmails: List[String] = List())(testCode: (String) => Any): Unit = {
    val newOwnerToken = newOwnerCreds.makeAuthToken()
    val project = claimGPAllocProject(newOwnerCreds, memberEmails)
    try {
      testCode(project.projectName)
    } finally {
      project.cleanup(newOwnerCreds, memberEmails)
    }
  }

  def addUserInBillingProject(billingProjectName: String, email: String, role: BillingProjectRole)
                             (implicit token: AuthToken): Unit = {
    Orchestration.billing.addUserToBillingProject(billingProjectName, email, role)
    register cleanUp Orchestration.billing.removeUserFromBillingProject(billingProjectName, email, role)
  }
}

