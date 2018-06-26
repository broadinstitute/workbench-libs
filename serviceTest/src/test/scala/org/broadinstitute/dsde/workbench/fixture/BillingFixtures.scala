package org.broadinstitute.dsde.workbench.fixture

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{ServiceTestConfig, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.model.{UserInfo, WorkbenchEmail, WorkbenchUserId}
import org.broadinstitute.dsde.workbench.service.{GPAlloc, Orchestration, Rawls}
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, RandomUtil}
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.scalatest.TestSuite

import scala.util.Try

/**
  * Mix in this trait to allow your test to access billing projects managed by the GPAlloc system, or create new
  * billing projects of your own.  Using GPAlloc will generally be much faster, limit the creation of billing projects
  * to those tests which truly require them.
  */
trait BillingFixtures extends ExceptionHandling with LazyLogging with CleanUp with RandomUtil {
  self: TestSuite =>

  protected def addMembersToBillingProject(projectName: String, memberEmails: List[String], role: BillingProjectRole)(implicit token: AuthToken): Unit = {
    memberEmails foreach { email =>
      Orchestration.billing.addUserToBillingProject(projectName, email, role)
    }
  }

  protected def removeMembersFromBillingProject(projectName: String, memberEmails: List[String], role: BillingProjectRole)(implicit token: AuthToken): Unit = {
    memberEmails foreach { email =>
      Orchestration.billing.removeUserFromBillingProject(projectName, email, role)
    }
  }

  @deprecated(message = "withBillingProject is deprecated. Use withCleanBillingProject if you want a billing project to isolate your tests, or withBrandNewBillingProject if you want to create a brand new one",
              since="workbench-service-test-0.5")
  def withBillingProject(namePrefix: String, ownerEmails: List[String] = List(), userEmails: List[String] = List())
                        (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    withBrandNewBillingProject(namePrefix, ownerEmails, userEmails)(testCode)(token)
  }

  case class ClaimedProject(projectName: String, gpAlloced: Boolean) {
    @deprecated(message = "cleanup with Credentials is deprecated. Use cleanup with AuthToken and email", since = "workbench-service-test-0.12")
    def cleanup(ownerCreds: Credentials): Unit = {
      val ownerToken = ownerCreds.makeAuthToken()
      if (gpAlloced)
        releaseGPAllocProject(projectName, ownerToken, ownerCreds.email)
      else {
        deleteBillingProject(projectName)(ownerToken)
      }
    }

    def cleanup(ownerToken: AuthToken, ownerEmail: String): Unit = {
        if (gpAlloced)
        releaseGPAllocProject(projectName, ownerToken, ownerEmail)
      else
        deleteBillingProject(projectName)(ownerToken)
    }
  }

  private def createNewBillingProject(namePrefix: String, ownerEmails: List[String] = List(), userEmails: List[String] = List())(implicit token: AuthToken): String = {
    val billingProjectName = randomIdWithPrefix(namePrefix)
    Orchestration.billing.createBillingProject(billingProjectName, ServiceTestConfig.Projects.billingAccountId)
    addMembersToBillingProject(billingProjectName, ownerEmails, BillingProjectRole.Owner)
    addMembersToBillingProject(billingProjectName, userEmails, BillingProjectRole.User)
    billingProjectName
  }

  private def deleteBillingProject(billingProjectName: String)(implicit token: AuthToken): Unit = {
    val projectOwnerInfo = UserInfo(OAuth2BearerToken(token.value), WorkbenchUserId(""), WorkbenchEmail("doesnt@matter.com"), 100)
    Rawls.admin.deleteBillingProject(billingProjectName, projectOwnerInfo)(UserPool.chooseAdmin.makeAuthToken())
  }

  /**
    * Create and use a new billing project.  Keep in mind that this is a slow an error-prone process, so you should only
    * use this method if your test requires it.
    *
    * @param namePrefix a short String to use as a billing project name prefix for identifying your test.
    * @param ownerEmails a List of emails (as Strings) to add as owners of this project
    * @param userEmails a List of emails (as Strings) to add as users of this project
    * @param testCode your test
    * @param token an AuthToken representing a billing project owner to pass to billing project endpoints
    */
  def withBrandNewBillingProject(namePrefix: String, ownerEmails: List[String] = List(), userEmails: List[String] = List())
                                (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val billingProjectName = createNewBillingProject(namePrefix, ownerEmails, userEmails)
    val testTrial = Try {
      testCode(billingProjectName)
    }

    val cleanupTrial = Try {
      deleteBillingProject(billingProjectName)
    }

    CleanUp.runCodeWithCleanup(testTrial, cleanupTrial)
  }

  /**
    * Manually claim a project provisioned by GPAlloc and optionally add members.
    * Consider using `withCleanBillingProject()` instead if you don't need to control the use of projects.
    *
    * @param newOwnerCreds Credentials representing a billing project owner to pass to billing project endpoints
    * @param ownerEmails a List of emails (as Strings) to add as owners of this project
    * @param userEmails a List of emails (as Strings) to add as users of this project
    * @return Some(GPAllocProject) if it succeeded, None if it did not
    */
  @deprecated(message = "claimGPAllocProject with Credentials is deprecated. Use claimGPAllocProject with AuthToken", since = "workbench-service-test-0.12")
  def claimGPAllocProject(newOwnerCreds: Credentials, ownerEmails: List[String] = List(), userEmails: List[String] = List()): ClaimedProject = {
    //request a GPAlloced project as the potential new owner
    val newOwnerToken = newOwnerCreds.makeAuthToken()
    GPAlloc.projects.requestProject(newOwnerToken) match {
      case Some(project) =>
        //the Rawls endpoint to register a precreated project needs to be called by a Rawls admin
        //but it also takes the new owner's UserInfo in order to create the resource as them in Sam
        val adminToken = UserPool.chooseAdmin.makeAuthToken()
        val newOwnerUserInfo = UserInfo(OAuth2BearerToken(newOwnerToken.value), WorkbenchUserId("0"), WorkbenchEmail(newOwnerCreds.email), 3600)
        Rawls.admin.claimProject(project.projectName, project.cromwellAuthBucketUrl, newOwnerUserInfo)(adminToken)

        addMembersToBillingProject(project.projectName, ownerEmails, BillingProjectRole.Owner)(newOwnerToken)
        addMembersToBillingProject(project.projectName, userEmails, BillingProjectRole.User)(newOwnerToken)

        ClaimedProject(project.projectName, gpAlloced = true)
      case _ =>
        logger.warn("claimGPAllocProject got no project back from GPAlloc. Falling back to making a brand new one...")
        val billingProjectName = createNewBillingProject("billingproj", ownerEmails, userEmails)(newOwnerToken)
        ClaimedProject(billingProjectName, gpAlloced = false)
    }
  }

  /**
    * Manually claim a project provisioned by GPAlloc and optionall add members.
    * As opposed to `Credentials`, accepts `AuthToken` and `String` values for the new owner.
    * This way a GPAlloc project can be claimed as a pet SA.
    *
    * @param newOwnerToken AuthToken for the new billing project owner to pass to billing project endpoints
    * @param newOwnerEmail Email for the new billing project owner
    * @param ownerEmails a List of emails (as Strings) to add as owners of this project
    * @param userEmails a List of emails (as Strings) to add as users of this project
    * @return Some(GPAllocProject) if it succeeded, none if it failed
    */
  def claimGPAllocProject(newOwnerToken: AuthToken, newOwnerEmail: String, ownerEmails: List[String] = List(), userEmails: List[String] = List()): ClaimedProject = {
    //request a GPAlloced project as the potential new owner
    GPAlloc.projects.requestProject(newOwnerToken) match {
      case Some(project) =>
        //the Rawls endpoint to register a precreated project needs to be called by a Rawls admin
        //but it also takes the new owner's UserInfo in order to create the resource as them in Sam
        val adminToken = UserPool.chooseAdmin.makeAuthToken()
        val newOwnerUserInfo = UserInfo(OAuth2BearerToken(newOwnerToken.value), WorkbenchUserId("0"), WorkbenchEmail(newOwnerEmail), 3600)
        Rawls.admin.claimProject(project.projectName, project.cromwellAuthBucketUrl, newOwnerUserInfo)(adminToken)

        addMembersToBillingProject(project.projectName, ownerEmails, BillingProjectRole.Owner)(newOwnerToken)
        addMembersToBillingProject(project.projectName, userEmails, BillingProjectRole.User)(newOwnerToken)

        ClaimedProject(project.projectName, gpAlloced = true)
      case _ =>
        logger.warn("claimGPAllocProject got no project back from GPAlloc. Falling back to making a brand new one...")
        val billingProjectName = createNewBillingProject("billingproj", ownerEmails, userEmails)(newOwnerToken)
        ClaimedProject(billingProjectName, gpAlloced = false)
    }
  }

  /**
    * Release a billing project back to GPAlloc when you are done with it.
    * Consider using `withCleanBillingProject()` instead if you don't need to control the use of projects.
    *
    * @param projectName the GPAllocProject to release
    * @param ownerCreds the Credentials of the current owner of the project
    */
  @deprecated(message = "releaseGPAllocProject with Credentials is deprecated. Use releaseGPAllocProject with AuthToken and email", since = "workbench-service-test-0.12")
  def releaseGPAllocProject(projectName: String, ownerCreds: Credentials): Unit = {
    val ownerToken = ownerCreds.makeAuthToken()
    val adminToken = UserPool.chooseAdmin.makeAuthToken()
    val newOwnerUserInfo = UserInfo(OAuth2BearerToken(ownerToken.value), WorkbenchUserId("0"), WorkbenchEmail(ownerCreds.email), 3600)

    Rawls.admin.releaseProject(projectName, newOwnerUserInfo)(adminToken)

    GPAlloc.projects.releaseProject(projectName)(ownerToken)
  }

  /**
    * Release a billing project back to GPAlloc when you are done with it.
    * Consider using `withCleanBillingProject()` instead if you don't need to control the use of projects.
    *
    * @param projectName the GPAllocProject to release
    * @param ownerToken the AuthToken of the current owner of the project
    * @param ownerEmail the email string of the current owner
    */
  def releaseGPAllocProject(projectName: String, ownerToken: AuthToken, ownerEmail: String): Unit = {
    val adminToken = UserPool.chooseAdmin.makeAuthToken()
    val newOwnerUserInfo = UserInfo(OAuth2BearerToken(ownerToken.value), WorkbenchUserId("0"), WorkbenchEmail(ownerEmail), 3600)
    Rawls.admin.releaseProject(projectName, newOwnerUserInfo)(adminToken)
    GPAlloc.projects.releaseProject(projectName)(ownerToken)
  }

  /**
    * Use a billing project provided by GPAlloc for the purpose of running tests against it.  This method will claim
    * a project for the duration of the test and release it when the test is done.
    *
    * @param newOwnerCreds Credentials representing a billing project owner to pass to billing project endpoints
    * @param ownerEmails a List of emails (as Strings) to add as owners of this project
    * @param userEmails a List of emails (as Strings) to add as users of this project
    * @param testCode your test
    */
  @deprecated(message = "withCleanBillingProject with Credentials is deprecated. Use withCleanBillingProject with AuthToken and email", since = "workbench-service-test-0.12")
  def withCleanBillingProject(newOwnerCreds: Credentials, ownerEmails: List[String] = List(), userEmails: List[String] = List())(testCode: (String) => Any): Unit = {
    val project = claimGPAllocProject(newOwnerCreds, ownerEmails, userEmails)
    val testTrial = Try {
      testCode(project.projectName)
    }
    val cleanupTrial = Try {
      project.cleanup(newOwnerCreds.makeAuthToken(), newOwnerCreds.email)
    }

    CleanUp.runCodeWithCleanup(testTrial, cleanupTrial)
  }

  /**
    * Use a billing project provided by GPAlloc for the purpose of running tests against it.  This method will claim
    * * a project for the duration of the test and release it when the test is done.
    *
    * @param newOwnerToken The AuthToken for the new billing project owner to pass to billing project endpoints
    * @param newOwnerEmail The email of the new billing project owner
    * @param ownerEmails a List of emails (as Strings) to add as owners of this project
    * @param userEmails a List of emails (as Strings) to add as users of this project
    * @param testCode your test
    */
  def withCleanBillingProject(newOwnerToken: AuthToken, newOwnerEmail: String, ownerEmails: List[String] = List(), userEmails: List[String] = List())(testCode: (String) => Any): Unit = {
    val project = claimGPAllocProject(newOwnerToken, newOwnerEmail, ownerEmails, userEmails)
    val testTrial = Try {
      testCode(project.projectName)
    }
    val cleanupTrial = Try {
      project.cleanup(newOwnerToken, newOwnerEmail)
    }

    CleanUp.runCodeWithCleanup(testTrial, cleanupTrial)
  }

  def addUserInBillingProject(billingProjectName: String, email: String, role: BillingProjectRole)
                             (implicit token: AuthToken): Unit = {
    Orchestration.billing.addUserToBillingProject(billingProjectName, email, role)
  }
}

