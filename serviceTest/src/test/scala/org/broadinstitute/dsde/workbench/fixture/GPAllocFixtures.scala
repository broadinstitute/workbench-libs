package org.broadinstitute.dsde.workbench.fixture

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchException}
import org.broadinstitute.dsde.workbench.service.{GPAlloc, GPAllocProject, Rawls}
import org.scalatest.{BeforeAndAfterAll, Suite, TestSuite}

import scala.collection.JavaConverters._


object GPAllocFixtures {
  //potentially, if people are using ParallelTestExecution, suites may run in parallel.
  //so these are synchronized java collections so we don't run into trouble.
  private var gpAllocedProjects: ConcurrentHashMap[String, AuthToken] = new ConcurrentHashMap[String, AuthToken]()
  private var suiteStack: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()

  /* A crude way of enforcing that tests are set up correctly.

    TLDR Guidance for test writers:

    * If you run only one test that extends GPAllocFixtures, Everything Will Be Fine™
    * If you're running more than one test that extends GPAllocFixtures, you need to put them in a
      scalatest Suites http://doc.scalatest.org/1.8/org/scalatest/Suites.html and mark the individual suites as @DoNotDiscover

    --- details below for the curious

    Rawls+Sam currently have no way to delete projects, so releasing the project back to GPAlloc risks getting
    the same project back in a later call to requestGPAllocedProject. That would make Rawls+Sam upset at having
    to set up a project they already know about.

    Without the ability to delete projects, we only know for sure that the project is unused when the test run
    is complete (at which point the Rawls + Sam databases will be wiped entirely). This means we have
    to register a hook that runs after ALL the tests are complete, and only ONCE. Since ScalaTest provides no
    "before and after the entire test run" mechanism, we are forced to mandate that all tests are nested inside
    a single outermost ScalaTest Suite that mixes in BeforeAndAfterAll and calls releaseAllGPAllocedProjects in
    its afterAll.

    This is further complicated by the use case of a developer who wants to run a single suite, not the entire
    nested outer suite; their projects need to be cleaned up too. We therefore require that test writers "register"
    their suite with this handler before requesting a GPAlloc project. This allows us to keep a "stack" of suites
    that may individually attempt to call releaseAllGPAllocedProjects; we ignore such calls unless they come from the
    bottom-most suite in the stack. If the tests are configured correctly, this bottom-most suite will either be the
    outermost super-Suite in which the others are nested, or then we're running a single suite on its own and all is well.

    Once releaseAllGPAllocedProjects is called successfully, we flip a switch that tells us to barf if someone attempts
    to GPAlloc further projects; that would indicate that tests are set up wrong and are thus at risk of upsetting Rawls+Sam
    with duplicate projects.
     */
  private val bNeverCleanUpAgain: AtomicBoolean = new AtomicBoolean(false)
  private val badTestStructure = "Your tests are set up wrong; they should be nested Suites. For more information, go here: " +
    "https://github.com/broadinstitute/gpalloc/USAGE.md"

  def registerGPAllocSuite(testSuite: Suite): Unit = {
    if(bNeverCleanUpAgain.get) {
      throw new WorkbenchException(badTestStructure)
    }
    suiteStack.add(testSuite.suiteName)
  }

  def requestGPAllocedProject(testSuite: Suite)(implicit authToken: AuthToken): Option[GPAllocProject] = {
    if( ! suiteStack.contains(testSuite.suiteName) ) {
      //The most likely developer error that would trigger this codepath: mixing in GPAllocFixtures to your suite,
      //then overriding beforeAll without calling super(). This stops us being able to clear up claimed projects.
      throw new WorkbenchException(
        "GPAlloc handler doesn't know who you are. Have you forgotten to call super() in your BeforeAndAfterAll overrides?")
    }

    val opt = GPAlloc.projects.requestProject
    opt foreach { p => gpAllocedProjects.put(p.projectName, authToken) }
    opt
  }

  def releaseAllGPAllocedProjects(testSuite: Suite): Unit = {
    if(bNeverCleanUpAgain.get) {
      throw new WorkbenchException(badTestStructure)
    }

    //only release if we've hit afterAll at the top of the suite stack
    if( suiteStack.get(0) == testSuite.suiteName ) {
      gpAllocedProjects.asScala foreach { case (proj, token) =>
        GPAlloc.projects.releaseProject(proj)(token)
      }
      bNeverCleanUpAgain.set(true)
    }
  }
}

/** Super-trait that can be mixed in with an instance of scalatest.Suites.
  * Will handle releasing GPAlloced projects at the end of the test run.
  */
trait GPAllocSuperFixture extends BeforeAndAfterAll { self: Suite =>
  override def beforeAll(): Unit = {
    GPAllocFixtures.registerGPAllocSuite(this)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    GPAllocFixtures.releaseAllGPAllocedProjects(this)
  }
}

/** Trait that can be mixed into a TestSuite.
  * Unless this is the only test suite that uses GPAlloc, you should also put your suites in a super-suite.
  * For more information, see https://github.com/broadinstitute/gpalloc/blob/develop/USAGE.md
  */
trait GPAllocFixtures extends BillingFixtures with GPAllocSuperFixture { self: TestSuite =>

  def withCleanBillingProject(newOwnerCreds: Credentials, memberEmails: List[String] = List())(testCode: (String) => Any): Unit = {
    //request a GPAlloced project as the potential new owner
    val newOwnerToken = newOwnerCreds.makeAuthToken()
    GPAllocFixtures.requestGPAllocedProject(self)(newOwnerCreds.makeAuthToken()) match {
      case Some(project) =>
        //call the Rawls endpoint to register a precreated project needs to be called by a Rawls admin
        val adminToken = UserPool.chooseAdmin.makeAuthToken()
        Rawls.admin.claimProject(project.projectName, project.cromwellAuthBucketUrl, newOwnerCreds.email)(adminToken)

        addMembersToBillingProject(project.projectName, memberEmails)(newOwnerToken)

        testCode(project.projectName)

        //see the giant comment at the top of the GPAllocFixtures companion object for an explanation
        //of why there's no corresponding Rawls.admin.releaseProject here
        removeMembersFromBillingProject(project.projectName, memberEmails)(newOwnerToken)
      case None =>
        logger.warn("withCleanBillingProject got no project back from GPAlloc. Falling back to making a brand new one...")
        withBrandNewBillingProject("billingproj")(testCode)(newOwnerToken)
    }
  }
}
