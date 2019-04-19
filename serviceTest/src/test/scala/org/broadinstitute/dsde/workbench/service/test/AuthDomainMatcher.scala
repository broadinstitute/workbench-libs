package org.broadinstitute.dsde.test.util

import akka.http.scaladsl.model.StatusCodes
import org.broadinstitute.dsde.workbench.config.Credentials
import org.broadinstitute.dsde.workbench.service.{Rawls, RestException}
import org.scalatest.Matchers

object AuthDomainMatcher extends Matchers {


  // NOTE:
  //  NotVisible -> Not found in workspace list
  //  NotAccessible -> Not authorized to see workspace detail

  val rawlsErrorMsg = "does not exist"

  /**
    * user can see the workspace in workspaces list and
    *  can see workspace detail and see expected auth-domains
    */
  def checkVisibleAndAccessible(user: Credentials, projectName: String, workspaceName: String, authDomains: List[String]): Unit = {

    val allWorkspacenames: Seq[String] = Rawls.workspaces.getWorkspaceNames()(user.makeAuthToken())
    allWorkspacenames should contain(workspaceName)

    val groups = Rawls.workspaces.getAuthDomainsInWorkspace(projectName, workspaceName)(user.makeAuthToken())
    groups should contain theSameElementsAs authDomains
  }

  /**
    * user can see the workspace in workspaces list BUT
    *  cannot see workspace detail
    */
  def checkVisibleNotAccessible(user: Credentials, projectName: String, workspaceName: String): Unit = {

    val workspacenames: Seq[String] = Rawls.workspaces.getWorkspaceNames()(user.makeAuthToken())
    workspacenames should contain(workspaceName)

    val exceptionMessage = intercept[RestException] {
      Rawls.workspaces.getWorkspaceDetails(projectName, workspaceName)(user.makeAuthToken())
    }
    exceptionMessage.message should include(StatusCodes.NotFound.intValue.toString)
    exceptionMessage.message should include(rawlsErrorMsg)
  }

  /**
    * user cannot see the workspace in workspaces list and
    *  cannot see workspace detail
    */
  def checkNotVisibleNotAccessible(user: Credentials, projectName: String, workspaceName: String): Unit = {

    val workspacenames = Rawls.workspaces.getWorkspaceNames()(user.makeAuthToken())
    workspacenames should not contain (workspaceName)

    val exceptionMessage = intercept[RestException] {
      Rawls.workspaces.getWorkspaceDetails(projectName, workspaceName)(user.makeAuthToken())
    }
    exceptionMessage.message should include(StatusCodes.NotFound.intValue.toString)
    exceptionMessage.message should include(rawlsErrorMsg)
  }
}
