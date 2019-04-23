package org.broadinstitute.dsde.test.util

import akka.http.scaladsl.model.StatusCodes
import org.broadinstitute.dsde.workbench.auth.AuthToken
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
  def checkVisibleAndAccessible(projectName: String, workspaceName: String, authDomains: List[String])(implicit token: AuthToken): Unit = {

    val allWorkspaceNames: Seq[String] = Rawls.workspaces.getWorkspaceNames()
    allWorkspaceNames should contain(workspaceName)

    val groups = Rawls.workspaces.getAuthDomainsInWorkspace(projectName, workspaceName)
    groups should contain theSameElementsAs authDomains
  }

  /**
    * user can see the workspace in workspaces list BUT
    *  cannot see workspace detail
    */
  def checkVisibleNotAccessible(projectName: String, workspaceName: String)(implicit token: AuthToken): Unit = {

    val workspaceNames: Seq[String] = Rawls.workspaces.getWorkspaceNames()
    workspaceNames should contain(workspaceName)

    val exceptionMessage = intercept[RestException] {
      Rawls.workspaces.getWorkspaceDetails(projectName, workspaceName)
    }
    exceptionMessage.message should include(StatusCodes.NotFound.intValue.toString)
    exceptionMessage.message should include(rawlsErrorMsg)
  }

  /**
    * user cannot see the workspace in workspaces list and
    *  cannot see workspace detail
    */
  def checkNotVisibleNotAccessible(projectName: String, workspaceName: String)(implicit token: AuthToken): Unit = {

    val workspaceNames = Rawls.workspaces.getWorkspaceNames()
    workspaceNames should not contain (workspaceName)

    val exceptionMessage = intercept[RestException] {
      Rawls.workspaces.getWorkspaceDetails(projectName, workspaceName)
    }
    exceptionMessage.message should include(StatusCodes.NotFound.intValue.toString)
    exceptionMessage.message should include(rawlsErrorMsg)
  }
}
