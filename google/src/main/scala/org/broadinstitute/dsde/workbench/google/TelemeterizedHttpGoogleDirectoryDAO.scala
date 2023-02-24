package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import cats.effect.IO
import com.google.api.services.directory.model.Group
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes.GoogleCredentialMode
import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchGroupName}
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics
import com.google.api.services.groupssettings.model.{Groups => GroupSettings}
import org.broadinstitute.dsde.workbench.model.google.ServiceAccount

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class TelemeterizedHttpGoogleDirectoryDAO(appName: String,
                                          googleCredentialMode: GoogleCredentialMode,
                                          workbenchMetricBaseName: String
)(implicit system: ActorSystem,
  executionContext: ExecutionContext,
  implicit val openTelemetry: OpenTelemetryMetrics[IO]
) extends HttpGoogleDirectoryDAO(appName, googleCredentialMode, workbenchMetricBaseName) {

  private val openTelemetryTags: Map[String, String] = Map("cloudPlatform" -> "GCP", "googleApi" -> "directory")

  @deprecated(message = "use createGroup(String, WorkbenchEmail) instead", since = "0.9")
  override def createGroup(groupName: WorkbenchGroupName, groupEmail: WorkbenchEmail): Future[Unit] =
    createGroup(groupName.value, groupEmail)

  override def createGroup(displayName: String,
                           groupEmail: WorkbenchEmail,
                           groupSettings: Option[GroupSettings]
  ): Future[Unit] =
    super
      .createGroup(displayName, groupEmail, groupSettings)
      .andThen {
        case Failure(_) => openTelemetry.incrementCounter("createGoogleGroupFailure", tags = openTelemetryTags)
        case Success(_) => openTelemetry.incrementCounter("createGoogleGroupSuccess", tags = openTelemetryTags)
      }

  override def deleteGroup(groupEmail: WorkbenchEmail): Future[Unit] =
    super
      .deleteGroup(groupEmail: WorkbenchEmail)
      .andThen {
        case Failure(_) => openTelemetry.incrementCounter("deleteGoogleGroupFailure", tags = openTelemetryTags)
        case Success(_) => openTelemetry.incrementCounter("deleteGoogleGroupSuccess", tags = openTelemetryTags)
      }

  override def addMemberToGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit] =
    super
      .addMemberToGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail)
      .andThen {
        case Failure(_) => openTelemetry.incrementCounter("addMemberToGoogleGroupFailure", tags = openTelemetryTags)
        case Success(_) => openTelemetry.incrementCounter("addMemberToGoogleGroupSuccess", tags = openTelemetryTags)
      }

  // See https://broadworkbench.atlassian.net/browse/CA-1005 about why we have a specific method for adding SA to groups
  override def addServiceAccountToGroup(groupEmail: WorkbenchEmail, serviceAccount: ServiceAccount): Future[Unit] =
    super
      .addServiceAccountToGroup(groupEmail: WorkbenchEmail, serviceAccount: ServiceAccount)
      .andThen {
        case Failure(_) =>
          openTelemetry.incrementCounter("addServiceAccountToGoogleGroupFailure", tags = openTelemetryTags)
        case Success(_) =>
          openTelemetry.incrementCounter("addServiceAccountToGoogleGroupSuccess", tags = openTelemetryTags)
      }

  override def removeMemberFromGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Unit] =
    super
      .removeMemberFromGroup(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail)
      .andThen {
        case Failure(_) =>
          openTelemetry.incrementCounter("removeMemberFromGoogleGroupFailure", tags = openTelemetryTags)
        case Success(_) =>
          openTelemetry.incrementCounter("removeMemberFromGoogleGroupSuccess", tags = openTelemetryTags)
      }

  override def getGoogleGroup(groupEmail: WorkbenchEmail): Future[Option[Group]] =
    super
      .getGoogleGroup(groupEmail: WorkbenchEmail)
      .andThen {
        case Failure(_) => openTelemetry.incrementCounter("getGoogleGroupFailure", tags = openTelemetryTags)
        case Success(_) => openTelemetry.incrementCounter("getGoogleGroupSuccess", tags = openTelemetryTags)
      }

  override def isGroupMember(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail): Future[Boolean] =
    super
      .isGroupMember(groupEmail: WorkbenchEmail, memberEmail: WorkbenchEmail)
      .andThen {
        case Failure(_) => openTelemetry.incrementCounter("isGoogleGroupMemberFailure", tags = openTelemetryTags)
        case Success(_) => openTelemetry.incrementCounter("isGoogleGroupMemberSuccess", tags = openTelemetryTags)
      }

  override def listGroupMembers(groupEmail: WorkbenchEmail): Future[Option[Seq[String]]] =
    super
      .listGroupMembers(groupEmail: WorkbenchEmail)
      .andThen {
        case Failure(_) => openTelemetry.incrementCounter("listGoogleGroupMembersFailure", tags = openTelemetryTags)
        case Success(_) => openTelemetry.incrementCounter("listGoogleGroupMembersSuccess", tags = openTelemetryTags)
      }
}
