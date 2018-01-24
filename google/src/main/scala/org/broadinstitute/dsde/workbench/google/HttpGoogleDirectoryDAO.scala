package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.http.HttpResponseException
import com.google.api.services.admin.directory.model.{Group, Member, Members}
import com.google.api.services.admin.directory.{Directory, DirectoryScopes}
import org.broadinstitute.dsde.workbench.google.GoogleCredentialMode._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mbemis on 8/17/17.
  */

class HttpGoogleDirectoryDAO(appName: String,
                             googleCredentialMode: GoogleCredentialMode,
                             workbenchMetricBaseName: String,
                             maxPageSize: Int = 200)
                            (implicit system: ActorSystem, executionContext: ExecutionContext)
  extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName) with GoogleDirectoryDAO {

  override val scopes = Seq(DirectoryScopes.ADMIN_DIRECTORY_GROUP)

  val groupMemberRole = "MEMBER" // the Google Group role corresponding to a member (note that this is distinct from the GCS roles defined in WorkspaceAccessLevel)

  override implicit val service = GoogleInstrumentedService.Groups

  override def createGroup(groupId: WorkbenchGroupName, groupEmail: WorkbenchEmail): Future[Unit] = createGroup(groupId.value, groupEmail)

  override def createGroup(displayName: String, groupEmail: WorkbenchEmail): Future[Unit] = {
    val directory = getGroupDirectory
    val groups = directory.groups
    val group = new Group().setEmail(groupEmail.value).setName(displayName.take(60)) //max google group name length is 60 characters
    val inserter = groups.insert(group)

    retryWhen500orGoogleError (() => { executeGoogleRequest(inserter) })
  }

  override def deleteGroup(groupEmail: WorkbenchEmail): Future[Unit] = {
    val directory = getGroupDirectory
    val groups = directory.groups
    val deleter = groups.delete(groupEmail.value)

    retryWithRecoverWhen500orGoogleError(() => {
      executeGoogleRequest(deleter)
      ()
    }) {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => () // if the group is already gone, don't fail
    }
  }

  override def addMemberToGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit] = {
    val member = new Member().setEmail(memberEmail.value).setRole(groupMemberRole)
    val inserter = getGroupDirectory.members.insert(groupEmail.value, member)

    retryWithRecoverWhen500orGoogleError(() => {
      executeGoogleRequest(inserter)
      ()
    }) {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.Conflict.intValue => () //if the member is already there, then don't keep trying to add them
      // Recover from http 412 errors because they can be spuriously thrown by Google, but the operation succeeds
      case e: HttpResponseException if e.getStatusCode == StatusCodes.PreconditionFailed.intValue => ()
    }
  }

  override def removeMemberFromGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit] = {
    val deleter = getGroupDirectory.members.delete(groupEmail.value, memberEmail.value)

    retryWithRecoverWhen500orGoogleError(() => {
      executeGoogleRequest(deleter)
      ()
    }) {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => () //if the member is already absent, then don't keep trying to delete them
    }
  }

  override def getGoogleGroup(groupEmail: WorkbenchEmail): Future[Option[Group]] = {
    val getter = getGroupDirectory.groups().get(groupEmail.value)

    retryWithRecoverWhen500orGoogleError(() => { Option(executeGoogleRequest(getter)) }){
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
    }
  }

  override def isGroupMember(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Boolean] = {
    val getter = getGroupDirectory.members.get(groupEmail.value, memberEmail.value)

    retryWithRecoverWhen500orGoogleError(() => {
      executeGoogleRequest(getter)
      true
    }) {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => false
    }
  }

  override def listGroupMembers(groupEmail: WorkbenchEmail): Future[Option[Seq[String]]] = {
    val fetcher = getGroupDirectory.members.list(groupEmail.value).setMaxResults(maxPageSize)

    import scala.collection.JavaConverters._
    listGroupMembersRecursive(fetcher) map { pagesOption =>
      pagesOption.map { pages =>
        pages.flatMap { page =>
          Option(page.getMembers.asScala) match {
            case None => Seq.empty
            case Some(members) => members.map(_.getEmail)
          }
        }
      }
    }
  }

  /**
    * recursive because the call to list all members is paginated.
    * @param fetcher
    * @param accumulated the accumulated Members objects, 1 for each page, the head element is the last prior request
    *                    for easy retrieval. The initial state is Some(Nil). This is what is eventually returned. This
    *                    is None when the group does not exist.
    * @return None if the group does not exist or a Members object for each page.
    */
  private def listGroupMembersRecursive(fetcher: Directory#Members#List, accumulated: Option[List[Members]] = Some(Nil)): Future[Option[List[Members]]] = {
    implicit val service = GoogleInstrumentedService.Groups
    accumulated match {
      // when accumulated has a Nil list then this must be the first request
      case Some(Nil) => retryWithRecoverWhen500orGoogleError(() => {
        Option(executeGoogleRequest(fetcher))
      }) {
        case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
      }.flatMap(firstPage => listGroupMembersRecursive(fetcher, firstPage.map(List(_))))

      // the head is the Members object of the prior request which contains next page token
      case Some(head :: xs) if head.getNextPageToken != null => retryWhen500orGoogleError(() => {
        executeGoogleRequest(fetcher.setPageToken(head.getNextPageToken))
      }).flatMap(nextPage => listGroupMembersRecursive(fetcher, accumulated.map(pages => nextPage :: pages)))

      // when accumulated is None (group does not exist) or next page token is null
      case _ => Future.successful(accumulated)
    }
  }

  private def getGroupDirectory = {
    new Directory.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

}
