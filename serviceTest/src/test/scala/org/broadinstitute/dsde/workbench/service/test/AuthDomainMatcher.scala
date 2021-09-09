package org.broadinstitute.dsde.test.util

import akka.http.scaladsl.model.StatusCodes
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.{Rawls, RestException}
import org.scalatest.matchers.must.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}

object AuthDomainMatcher extends Matchers with Eventually {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(150, Seconds)), interval = scaled(Span(5, Seconds)))

  // NOTE:
  //  NotVisible -> Not found in workspace list
  //  NotAccessible -> Not authorized to see workspace detail

  val rawlsErrorMsg = "does not exist"

  /**
   * user can see the workspace in workspaces list and can see workspace detail and see expected auth-domains
   */
  def checkVisibleAndAccessible(projectName: String, workspaceName: String, authDomains: List[String])(implicit
    token: AuthToken
  ): Unit = {

    eventually {
      val allWorkspaceNames: Seq[String] = Rawls.workspaces.getWorkspaceNames()
      allWorkspaceNames must contain(workspaceName)
    }

    eventually {
      val groups = Rawls.workspaces.getAuthDomainsInWorkspace(projectName, workspaceName)
      groups must contain theSameElementsAs authDomains
    }
  }

  /**
   * user can see the workspace in workspaces list BUT cannot see workspace detail
   */
  def checkVisibleNotAccessible(projectName: String, workspaceName: String)(implicit token: AuthToken): Unit = {

    eventually {
      val workspaceNames: Seq[String] = Rawls.workspaces.getWorkspaceNames()
      workspaceNames must contain(workspaceName)
    }

    eventually {
      val exceptionMessage = intercept[RestException] {
        Rawls.workspaces.getWorkspaceDetails(projectName, workspaceName)
      }
      exceptionMessage.message must include(StatusCodes.NotFound.intValue.toString)
      exceptionMessage.message must include(rawlsErrorMsg)
    }
  }

  /**
   * user cannot see the workspace in workspaces list and cannot see workspace detail
   */
  def checkNotVisibleNotAccessible(projectName: String, workspaceName: String)(implicit token: AuthToken): Unit = {

    eventually {
      val workspaceNames = Rawls.workspaces.getWorkspaceNames()
      workspaceNames must not contain workspaceName
    }

    eventually {
      val exceptionMessage = intercept[RestException] {
        Rawls.workspaces.getWorkspaceDetails(projectName, workspaceName)
      }
      exceptionMessage.message must include(StatusCodes.NotFound.intValue.toString)
      exceptionMessage.message must include(rawlsErrorMsg)
    }
  }

}
