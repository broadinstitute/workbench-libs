package org.broadinstitute.dsde.workbench.google

import com.google.api.services.admin.directory.model.Group
import org.broadinstitute.dsde.workbench.model._
import com.google.api.services.groupssettings.model.{Groups => GroupSettings}
import org.broadinstitute.dsde.workbench.model.google.ServiceAccount

import scala.concurrent.Future

/**
 * Created by mbemis on 8/17/17.
 */
trait GoogleDirectoryDAO {

  @deprecated(message = "use createGroup(String, WorkbenchEmail) instead", since = "0.9")
  def createGroup(groupName: WorkbenchGroupName, groupEmail: WorkbenchEmail): Future[Unit]
  def createGroup(displayName: String,
                  groupEmail: WorkbenchEmail,
                  groupSettings: Option[GroupSettings] = None
  ): Future[Unit]
  def deleteGroup(groupEmail: WorkbenchEmail): Future[Unit]
  def addMemberToGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit]
  // See https://broadworkbench.atlassian.net/browse/CA-1005 about why we have a specific method for adding SA to groups
  def addServiceAccountToGroup(groupEmail: WorkbenchEmail, serviceAccount: ServiceAccount): Future[Unit]
  def removeMemberFromGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit]
  def getGoogleGroup(groupEmail: WorkbenchEmail): Future[Option[Group]]
  def isGroupMember(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Boolean]
  def listGroupMembers(groupEmail: WorkbenchEmail): Future[Option[Seq[String]]]

  def lockedDownGroupSettings =
    new GroupSettings()
      .setWhoCanAdd("ALL_OWNERS_CAN_ADD")
      .setWhoCanJoin("INVITED_CAN_JOIN")
      .setWhoCanViewMembership("ALL_MANAGERS_CAN_VIEW")
      .setWhoCanViewGroup("ALL_OWNERS_CAN_VIEW")
      .setWhoCanInvite("NONE_CAN_INVITE")
      .setArchiveOnly(
        "true"
      ) //.setWhoCanPostMessage("NONE_CAN_POST") setting archive only is the way to set it so no one can post
      .setWhoCanLeaveGroup("NONE_CAN_LEAVE")
      .setWhoCanContactOwner("ALL_MANAGERS_CAN_CONTACT")
      .setWhoCanAddReferences("NONE")
      .setWhoCanAssignTopics("NONE")
      .setWhoCanUnassignTopic("NONE")
      .setWhoCanTakeTopics("NONE")
      .setWhoCanMarkDuplicate("NONE")
      .setWhoCanMarkNoResponseNeeded("NONE")
      .setWhoCanMarkFavoriteReplyOnAnyTopic("NONE")
      .setWhoCanMarkFavoriteReplyOnOwnTopic("NONE")
      .setWhoCanUnmarkFavoriteReplyOnAnyTopic("NONE")
      .setWhoCanEnterFreeFormTags("NONE")
      .setWhoCanModifyTagsAndCategories("NONE")

}
