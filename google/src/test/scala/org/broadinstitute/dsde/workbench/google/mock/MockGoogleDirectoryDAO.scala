package org.broadinstitute.dsde.workbench.google.mock

import com.google.api.services.admin.directory.model.Group
import org.broadinstitute.dsde.workbench.google.GoogleDirectoryDAO
import org.broadinstitute.dsde.workbench.model._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mbemis on 8/23/17.
  */
class MockGoogleDirectoryDAO( implicit val executionContext: ExecutionContext ) extends GoogleDirectoryDAO {

  val groups: TrieMap[WorkbenchGroupEmail, Set[WorkbenchEmail]] = TrieMap()

  override def createGroup(groupName: WorkbenchGroupName, groupEmail: WorkbenchGroupEmail): Future[Unit] = {
    Future.successful(groups.putIfAbsent(groupEmail, Set.empty))
  }

  override def deleteGroup(groupEmail: WorkbenchGroupEmail): Future[Unit] = {
    Future.successful(groups.remove(groupEmail))
  }

  override def addMemberToGroup(groupEmail: WorkbenchGroupEmail, memberEmail: WorkbenchEmail): Future[Unit] = {
    Future {
      val currentMembers = groups.getOrElse(groupEmail, throw new NoSuchElementException(s"group ${groupEmail.value} not found"))
      val newMembersList = currentMembers + memberEmail

      groups.put(groupEmail, newMembersList)
    }
  }

  override def removeMemberFromGroup(groupEmail: WorkbenchGroupEmail, memberEmail: WorkbenchEmail): Future[Unit] = {
    Future {
      val currentMembers = groups.getOrElse(groupEmail, throw new NoSuchElementException(s"group ${groupEmail.value} not found"))
      val newMembersList = currentMembers - memberEmail

      groups.put(groupEmail, newMembersList)
    }
  }

  override def getGoogleGroup(groupEmail: WorkbenchGroupEmail): Future[Option[Group]] = {
    Future.successful(groups.get(groupEmail).map { _ =>
      val googleGroup = new Group()
      googleGroup.setEmail(groupEmail.value)
      googleGroup
    })
  }

  override def isGroupMember(groupEmail: WorkbenchGroupEmail, memberEmail: WorkbenchEmail): Future[Boolean] = {
    Future {
      val currentMembers = groups.getOrElse(groupEmail, throw new NoSuchElementException(s"group ${groupEmail.value} not found"))
      currentMembers.map(_.value).contains(memberEmail.value)
    }
  }

}
