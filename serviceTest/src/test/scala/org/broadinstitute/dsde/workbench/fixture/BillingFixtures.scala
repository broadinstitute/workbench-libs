package org.broadinstitute.dsde.workbench.fixture

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, ServiceTestConfig, UserPool}
import org.broadinstitute.dsde.workbench.model.{UserInfo, WorkbenchEmail, WorkbenchUserId}
import org.broadinstitute.dsde.workbench.service._
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, RandomUtil}
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.broadinstitute.dsde.workbench.util.Retry
import org.scalatest.TestSuite
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationDouble
import scala.util.{Random, Try}


/**
  * Mix in this trait to allow your test to access billing projects managed by the GPAlloc system, or create new
  * billing projects of your own.  Using GPAlloc will generally be much faster, limit the creation of billing projects
  * to those tests which truly require them.
  */
trait BillingFixtures extends ExceptionHandling with LazyLogging with CleanUp with RandomUtil with Retry with ScalaFutures {
  self: TestSuite =>

  implicit val system = ActorSystem()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

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
    def cleanup(ownerCreds: Credentials): Unit = {
      cleanup(ownerCreds.email)(ownerCreds.makeAuthToken _)
    }

    def cleanup(ownerEmail: String)(ownerToken: () => AuthToken): Unit = {
      if (gpAlloced)
        releaseGPAllocProject(projectName, ownerEmail)(ownerToken)
      else
        deleteBillingProject(projectName)(ownerToken())
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
  def claimGPAllocProject(newOwnerCreds: Credentials, ownerEmails: List[String] = List(), userEmails: List[String] = List()): ClaimedProject = {
    claimGPAllocProject(newOwnerCreds.email, ownerEmails, userEmails)(newOwnerCreds.makeAuthToken _)
  }

  /**
    * Manually claim a project provisioned by GPAlloc and optionall add members.
    * As opposed to `Credentials`, accepts `AuthToken` and `String` values for the new owner.
    * This way a GPAlloc project can be claimed as a pet SA.
    *
    * @param newOwnerEmail Email for the new billing project owner
    * @param ownerEmails a List of emails (as Strings) to add as owners of this project
    * @param userEmails a List of emails (as Strings) to add as users of this project
    * @param newOwnerToken Function that returns an AuthToken for the new billing project owner to pass to billing project endpoints
    * @return Some(GPAllocProject) if it succeeded, none if it failed
    */
  def claimGPAllocProject(newOwnerEmail: String, ownerEmails: List[String], userEmails: List[String])(newOwnerToken: () => AuthToken): ClaimedProject = {
    //request a GPAlloced project as the potential new owner
    val retryFuture: RetryableFuture[ClaimedProject] = retry[ClaimedProject](failureLogMessage = "Retry claim GPAlloc billing project") { () =>
      val requestFuture: Future[Option[GPAllocProject]] = Future(GPAlloc.projects.requestProject(newOwnerToken()))
      requestFuture.map {
        case Some(project) =>
          //the Rawls endpoint to register a precreated project needs to be called by a Rawls admin
          //but it also takes the new owner's UserInfo in order to create the resource as them in Sam
          val admin = UserPool.chooseAdmin
          val adminToken = admin.makeAuthToken()
          val newOwnerUserInfo = UserInfo(OAuth2BearerToken(newOwnerToken().value), WorkbenchUserId("0"), WorkbenchEmail(newOwnerEmail), 3600)
          try {
            Rawls.admin.claimProject(project.projectName, project.cromwellAuthBucketUrl, newOwnerUserInfo)(adminToken)
          } catch {
            case e: Exception =>
              // Rawls claim project request sometimes fail
              // e.g. of error "Cannot create billing project [gpalloc-qa-master-2z4jdey] in database because it already exists"
              logger.warn(s"ERROR occurred in claimGPAllocProject. Release unusable billing project ${project.projectName}.")
              Rawls.admin.releaseProject(project.projectName, newOwnerUserInfo)(adminToken)
              GPAlloc.projects.releaseProject(project.projectName)(newOwnerToken())
              Thread sleep Random.nextInt(30000)
              throw e
          }
          if (ownerEmails.nonEmpty) {
            addMembersToBillingProject(project.projectName, ownerEmails, BillingProjectRole.Owner)(newOwnerToken())
          }
          if (userEmails.nonEmpty) {
            addMembersToBillingProject(project.projectName, userEmails, BillingProjectRole.User)(newOwnerToken())
          }
          ClaimedProject(project.projectName, gpAlloced = true)
        case _ =>
          logger.warn("claimGPAllocProject got no project back from GPAlloc. Falling back to making a brand new one...")
          val billingProjectName = createNewBillingProject("billingproj", ownerEmails, userEmails)(newOwnerToken())
          ClaimedProject(billingProjectName, gpAlloced = false)
      }
    }

    Await.result(retryFuture, 15.minutes)
  }

  /**
    * Release a billing project back to GPAlloc when you are done with it.
    * Consider using `withCleanBillingProject()` instead if you don't need to control the use of projects.
    *
    * @param projectName the GPAllocProject to release
    * @param ownerCreds the Credentials of the current owner of the project
    */
  def releaseGPAllocProject(projectName: String, ownerCreds: Credentials): Unit = {
    releaseGPAllocProject(projectName, ownerCreds.email)(ownerCreds.makeAuthToken _)
  }

  /**
    * Release a billing project back to GPAlloc when you are done with it.
    * Consider using `withCleanBillingProject()` instead if you don't need to control the use of projects.
    *
    * @param projectName the GPAllocProject to release
    * @param ownerEmail the email string of the current owner
    * @param ownerToken Function that returns the AuthToken of the current owner of the project
    */
  def releaseGPAllocProject(projectName: String, ownerEmail: String)(ownerToken: () => AuthToken): Unit = {
    val adminToken = UserPool.chooseAdmin.makeAuthToken()
    val newOwnerUserInfo = UserInfo(OAuth2BearerToken(ownerToken().value), WorkbenchUserId("0"), WorkbenchEmail(ownerEmail), 3600)
    Rawls.admin.releaseProject(projectName, newOwnerUserInfo)(adminToken)
    GPAlloc.projects.releaseProject(projectName)(ownerToken())
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
  def withCleanBillingProject(newOwnerCreds: Credentials, ownerEmails: List[String] = List(), userEmails: List[String] = List())(testCode: (String) => Any): Unit = {
    withCleanBillingProject(newOwnerCreds.email, ownerEmails, userEmails)(newOwnerCreds.makeAuthToken _)(testCode)
  }

  /**
    * Use a billing project provided by GPAlloc for the purpose of running tests against it.  This method will claim
    * * a project for the duration of the test and release it when the test is done.
    *
    * @param newOwnerEmail The email of the new billing project owner
    * @param ownerEmails a List of emails (as Strings) to add as owners of this project
    * @param userEmails a List of emails (as Strings) to add as users of this project
    * @param newOwnerToken Function that returns an AuthToken for the new billing project owner to pass to billing project endpoints
    * @param testCode your test
    */
  def withCleanBillingProject(newOwnerEmail: String, ownerEmails: List[String], userEmails: List[String])(newOwnerToken: () => AuthToken)(testCode: (String) => Any): Unit = {
    val project = claimGPAllocProject(newOwnerEmail, ownerEmails, userEmails)(newOwnerToken)
    val testTrial = Try {
      testCode(project.projectName)
    }
    val cleanupTrial = Try {
      project.cleanup(newOwnerEmail)(newOwnerToken)
    }

    CleanUp.runCodeWithCleanup(testTrial, cleanupTrial)
  }

  def addUserInBillingProject(billingProjectName: String, email: String, role: BillingProjectRole)
                             (implicit token: AuthToken): Unit = {
    Orchestration.billing.addUserToBillingProject(billingProjectName, email, role)
  }
}
