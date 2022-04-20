package org.broadinstitute.dsde.workbench.google

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import com.google.api.services.admin.directory.model.{Group, Member, Members}
import com.google.api.services.admin.directory.{Directory, DirectoryScopes}
import com.google.api.services.groupssettings.model.{Groups => GroupSettings}
import com.google.api.services.groupssettings.{Groupssettings, GroupssettingsScopes}
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GoogleUtilities.RetryPredicates._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.model.google.ServiceAccount

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Created by mbemis on 8/17/17.
 */
class HttpGoogleDirectoryDAO(appName: String,
                             googleCredentialMode: GoogleCredentialMode,
                             workbenchMetricBaseName: String,
                             maxPageSize: Int = 200
)(implicit system: ActorSystem, executionContext: ExecutionContext)
    extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName)
    with GoogleDirectoryDAO {

  /**
   * This is a nested class because the scopes need to be different and having one credential with scopes for both
   * DirectoryScopes.ADMIN_DIRECTORY_GROUP and GroupssettingsScopes.APPS_GROUPS_SETTINGS results in a 401 error
   * getting a token. It does not feel right to expose this as a top level class. I don't know why google decided
   * to split out this api but I feel like it should be bundled.
   */
  private class GroupSettingsDAO()
      extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName) {
    implicit override val service = GoogleInstrumentedService.Groups
    override val scopes = Seq(GroupssettingsScopes.APPS_GROUPS_SETTINGS)
    private lazy val settingsClient =
      new Groupssettings.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()

    def updateGroupSettings(groupEmail: WorkbenchEmail, settings: GroupSettings) = {
      val updater = settingsClient.groups().update(groupEmail.value, settings)
      retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
        executeGoogleRequest(updater)
      }
    }
  }

  @deprecated(
    message =
      "This way of instantiating HttpGoogleDirectoryDAO has been deprecated. Please update to use the primary constructor.",
    since = "0.15"
  )
  def this(serviceAccountClientId: String,
           pemFile: String,
           ubEmail: String,
           appsDomain: String,
           appName: String,
           workbenchMetricBaseName: String,
           maxPageSize: Int
  )(implicit system: ActorSystem, executionContext: ExecutionContext) =
    this(appName,
         Pem(WorkbenchEmail(serviceAccountClientId), new File(pemFile), Some(WorkbenchEmail(ubEmail))),
         workbenchMetricBaseName,
         maxPageSize
    )

  @deprecated(
    message =
      "This way of instantiating HttpGoogleDirectoryDAO has been deprecated. Please update to use the primary constructor.",
    since = "0.15"
  )
  def this(clientSecrets: GoogleClientSecrets,
           pemFile: String,
           appsDomain: String,
           appName: String,
           workbenchMetricBaseName: String
  )(implicit system: ActorSystem, executionContext: ExecutionContext) =
    this(appName,
         Pem(
           WorkbenchEmail(clientSecrets.getDetails.get("client_email").toString),
           new File(pemFile),
           Some(WorkbenchEmail(clientSecrets.getDetails.get("sub_email").toString))
         ),
         workbenchMetricBaseName
    )

  override val scopes = Seq(DirectoryScopes.ADMIN_DIRECTORY_GROUP)

  val groupMemberRole =
    "MEMBER" // the Google Group role corresponding to a member (note that this is distinct from the GCS roles defined in WorkspaceAccessLevel)

  implicit override val service = GoogleInstrumentedService.Groups

  private lazy val directory =
    new Directory.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()

  override def createGroup(groupId: WorkbenchGroupName, groupEmail: WorkbenchEmail): Future[Unit] =
    createGroup(groupId.value, groupEmail)

  override def createGroup(displayName: String,
                           groupEmail: WorkbenchEmail,
                           groupSettings: Option[GroupSettings] = None
  ): Future[Unit] = {
    val groups = directory.groups
    val group = new Group()
      .setEmail(groupEmail.value)
      .setName(displayName.take(60)) // max google group name length is 60 characters
    val inserter = groups.insert(group)

    for {
      _ <- retryWithRecover(when5xx,
                            whenUsageLimited,
                            when404,
                            whenInvalidValueOnBucketCreation,
                            whenNonHttpIOException
      ) { () =>
        executeGoogleRequest(inserter)
      } {
        case t: Throwable if when5xx(t) =>
          // sometimes creating a group errors with a 5xx error and partially creates the group
          // when this happens some group apis (create, list members and delete group) say the group exists
          // while others (add member, get details) say the group does not exist.
          // calling delete before retrying the create should clean all that up
          logger.debug(
            s"Creating Google group ${displayName.take(60)} with email ${groupEmail.value} returned a 5xx error. Deleting partially created group and trying again..."
          )
          Try(executeGoogleRequest(groups.delete(groupEmail.value)))
          throw t
      }
      _ <- groupSettings match {
        case None           => Future.successful(())
        case Some(settings) => new GroupSettingsDAO().updateGroupSettings(groupEmail, settings)
      }
    } yield ()
  }

  override def deleteGroup(groupEmail: WorkbenchEmail): Future[Unit] = {
    val groups = directory.groups
    val deleter = groups.delete(groupEmail.value)

    retryWithRecover(when5xx,
                     whenUsageLimited,
                     when404,
                     whenInvalidValueOnBucketCreation,
                     whenPreconditionFailed,
                     whenNonHttpIOException
    ) { () =>
      executeGoogleRequest(deleter)
      ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue =>
        () // if the group is already gone, don't fail
    }
  }

  override def addMemberToGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit] = {
    val member = new Member().setEmail(memberEmail.value)
    addMemberToGroup(groupEmail, member)
  }

  override def addServiceAccountToGroup(groupEmail: WorkbenchEmail, serviceAccount: ServiceAccount): Future[Unit] = {
    val member = new Member().setId(serviceAccount.subjectId.value)
    addMemberToGroup(groupEmail, member)
  }

  private def addMemberToGroup(groupEmail: WorkbenchEmail, member: Member): Future[Unit] = {
    val finalMember = member.setRole(groupMemberRole)
    val inserter = directory.members.insert(groupEmail.value, finalMember)

    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(inserter)
        ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.Conflict.intValue =>
        () // if the member is already there, then don't keep trying to add them
      // Recover from http 412 errors because they can be spuriously thrown by Google, but the operation succeeds
      case e: HttpResponseException if e.getStatusCode == StatusCodes.PreconditionFailed.intValue => ()
    }
  }

  override def removeMemberFromGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit] = {
    val deleter = directory.members.delete(groupEmail.value, memberEmail.value)

    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(deleter)
        ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue =>
        () // if the member is already absent, then don't keep trying to delete them
      case e: GoogleJsonResponseException
          if e.getStatusCode == StatusCodes.BadRequest.intValue && e.getDetails.getMessage.equals(
            "Missing required field: memberKey"
          ) =>
        () // this means the member does not exist
    }
  }

  override def getGoogleGroup(groupEmail: WorkbenchEmail): Future[Option[Group]] = {
    val getter = directory.groups().get(groupEmail.value)

    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        Option(executeGoogleRequest(getter))
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
    }
  }

  override def isGroupMember(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Boolean] = {
    val getter = directory.members.get(groupEmail.value, memberEmail.value)

    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(getter)
        true
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => false
      case e: GoogleJsonResponseException
          if e.getStatusCode == StatusCodes.BadRequest.intValue && e.getDetails.getMessage.equals(
            "Missing required field: memberKey"
          ) =>
        false // this means the member does not exist
    }
  }

  override def listGroupMembers(groupEmail: WorkbenchEmail): Future[Option[Seq[String]]] = {
    val fetcher = directory.members.list(groupEmail.value).setMaxResults(maxPageSize)

    import scala.jdk.CollectionConverters._
    listGroupMembersRecursive(fetcher) map { pagesOption =>
      pagesOption.map { pages =>
        pages.flatMap { page =>
          Option(page.getMembers.asScala) match {
            case None          => Seq.empty
            case Some(members) => members.map(_.getEmail)
          }
        }
      }
    }
  }

  /**
   * recursive because the call to list all members is paginated.
   *
   * @param fetcher
   * @param accumulated the accumulated Members objects, 1 for each page, the head element is the last prior request
   *                    for easy retrieval. The initial state is Some(Nil). This is what is eventually returned. This
   *                    is None when the group does not exist.
   * @return None if the group does not exist or a Members object for each page.
   */
  private def listGroupMembersRecursive(
    fetcher: Directory#Members#List,
    accumulated: Option[List[Members]] = Some(Nil)
  ): Future[Option[List[Members]]] = {
    implicit val service = GoogleInstrumentedService.Groups
    accumulated match {
      // when accumulated has a Nil list then this must be the first request
      case Some(Nil) =>
        retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
          () =>
            Option(executeGoogleRequest(fetcher))
        } {
          case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
        }.flatMap(firstPage => listGroupMembersRecursive(fetcher, firstPage.map(List(_))))

      // the head is the Members object of the prior request which contains next page token
      case Some(head :: _) if head.getNextPageToken != null =>
        retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
          executeGoogleRequest(fetcher.setPageToken(head.getNextPageToken))
        }.flatMap(nextPage => listGroupMembersRecursive(fetcher, accumulated.map(pages => nextPage :: pages)))

      // when accumulated is None (group does not exist) or next page token is null
      case _ => Future.successful(accumulated)
    }
  }

}
