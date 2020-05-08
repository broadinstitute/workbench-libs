package org.broadinstitute.dsde.workbench.google.mock

import com.google.api.services.admin.directory.model.Group
import com.google.api.services.groupssettings.model.{Groups => GroupSettings}
import org.broadinstitute.dsde.workbench.google.GoogleDirectoryDAO
import org.broadinstitute.dsde.workbench.model._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by mbemis on 8/23/17.
 */
class MockGoogleDirectoryDAO(implicit val executionContext: ExecutionContext) extends GoogleDirectoryDAO {

  val groups: TrieMap[WorkbenchEmail, Set[WorkbenchEmail]] = TrieMap()

  override def createGroup(groupName: WorkbenchGroupName, groupEmail: WorkbenchEmail): Future[Unit] =
    createGroup(groupName.value, groupEmail)

  override def createGroup(displayName: String,
                           groupEmail: WorkbenchEmail,
                           groupSettings: Option[GroupSettings] = None): Future[Unit] =
    Future.successful(groups.putIfAbsent(groupEmail, Set.empty))

  override def deleteGroup(groupEmail: WorkbenchEmail): Future[Unit] =
    Future.successful(groups.remove(groupEmail))

  override def addMemberToGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit] =
    Future {
      val currentMembers =
        groups.getOrElse(groupEmail, throw new NoSuchElementException(s"group ${groupEmail.value} not found"))
      val newMembersList = currentMembers + memberEmail

      groups.put(groupEmail, newMembersList)
    }

  override def removeMemberFromGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit] =
    Future {
      val currentMembers =
        groups.getOrElse(groupEmail, throw new NoSuchElementException(s"group ${groupEmail.value} not found"))
      val newMembersList = currentMembers - memberEmail

      groups.put(groupEmail, newMembersList)
    }

  override def getGoogleGroup(groupEmail: WorkbenchEmail): Future[Option[Group]] =
    Future.successful(groups.get(groupEmail).map { _ =>
      val googleGroup = new Group()
      googleGroup.setEmail(groupEmail.value)
      googleGroup
    })

  override def isGroupMember(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Boolean] =
    Future {
      val currentMembers =
        groups.getOrElse(groupEmail, throw new NoSuchElementException(s"group ${groupEmail.value} not found"))
      currentMembers.map(_.value).contains(memberEmail.value)
    }

  override def listGroupMembers(groupEmail: WorkbenchEmail): Future[Option[Seq[String]]] = Future {
    groups.get(groupEmail).map(_.map(_.value).toSeq)
  }
}
