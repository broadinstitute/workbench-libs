package org.broadinstitute.dsde.workbench.google

import com.google.api.services.admin.directory.model.Group
import org.broadinstitute.dsde.workbench.model._

import scala.concurrent.Future

/**
  * Created by mbemis on 8/17/17.
  */

trait GoogleDirectoryDAO {

  def createGroup(groupName: WorkbenchGroupName, groupEmail: WorkbenchGroupEmail): Future[Unit]
  def deleteGroup(groupEmail: WorkbenchGroupEmail): Future[Unit]
  def addMemberToGroup(groupEmail: WorkbenchGroupEmail, memberEmail: WorkbenchEmail): Future[Unit]
  def removeMemberFromGroup(groupEmail: WorkbenchGroupEmail, memberEmail: WorkbenchEmail): Future[Unit]
  def getGoogleGroup(groupEmail: WorkbenchGroupEmail): Future[Option[Group]]
  def isGroupMember(groupEmail: WorkbenchGroupEmail, memberEmail: WorkbenchEmail): Future[Boolean]

}
