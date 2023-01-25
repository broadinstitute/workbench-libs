package org.broadinstitute.dsde.workbench.model

import org.broadinstitute.dsde.workbench.model.WorkbenchIdentityJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.reflect.runtime.universe._

/**
 * All notifications emitted by workbench are described here. To add a new notification type:
 * - create a new case class with appropriate fields
 *   - extend WorkspaceNotification if it is a notification specific to a workspace
 *   - otherwise extend UserNotification if a user id is available
 * - create a val extending NotificationType or WorkspaceNotificationType being sure to call register
 */
object Notifications {
  private def baseKey(n: Notification) = s"notifications/${n.getClass.getSimpleName}"
  private def baseKey[T <: Notification: TypeTag] = s"notifications/${typeOf[T].typeSymbol.asClass.name}"

  private def workspaceKey(baseKey: String, workspaceName: WorkspaceName) =
    s"$baseKey/${workspaceName.namespace}/${workspaceName.name}"

  case class WorkspaceName(namespace: String, name: String)
  implicit val WorkspaceNameFormat = jsonFormat2(WorkspaceName)

  sealed abstract class NotificationType[T <: Notification: TypeTag] {
    def baseKey = Notifications.baseKey[T]
    def workspaceNotification = typeOf[T] <:< typeOf[WorkspaceNotification]
    val format: RootJsonFormat[T]
    val notificationType = typeOf[T].typeSymbol.asClass.name.toString
    val description: String

    /** means the user can never turn it off */
    val alwaysOn = false
  }

  sealed abstract class WorkspaceNotificationType[T <: WorkspaceNotification: TypeTag] extends NotificationType[T] {
    def workspaceKey(workspaceName: WorkspaceName) = Notifications.workspaceKey(Notifications.baseKey[T], workspaceName)
  }

  sealed trait Notification {
    def key = Notifications.baseKey(this)
  }

  sealed trait UserNotification extends Notification {
    val recipientUserId: WorkbenchUserId
  }
  object UserNotification {
    def unapply(userNotification: UserNotification) = Option(userNotification.recipientUserId)
  }

  sealed trait WorkspaceNotification extends UserNotification {
    override def key = Notifications.workspaceKey(Notifications.baseKey(this), workspaceName)
    val workspaceName: WorkspaceName
  }
  object WorkspaceNotification {
    def unapply(workspaceNotification: WorkspaceNotification) =
      Option((workspaceNotification.workspaceName, workspaceNotification.recipientUserId))
  }

  private val allNotificationTypesBuilder = Map.newBuilder[String, NotificationType[_ <: Notification]]

  /**
   * called internally to register a notification type so it will appear in the allNotificationTypes map
   * @param notificationType
   * @tparam T
   * @return notificationType
   */
  private def register[T <: Notification](notificationType: NotificationType[T]): NotificationType[T] = {
    require(allNotificationTypes == null,
            "all calls to register must come before definition of allNotificationTypes in the file"
    )
    allNotificationTypesBuilder += notificationType.notificationType -> notificationType
    notificationType
  }

  case class ActivationNotification(recipientUserId: WorkbenchUserId) extends UserNotification
  val ActivationNotificationType = register(new NotificationType[ActivationNotification] {
    override val format = jsonFormat1(ActivationNotification.apply)
    override val description = "Account Activation"
    override val alwaysOn = true
  })

  case class AzurePreviewActivationNotification(recipientUserId: WorkbenchUserId) extends UserNotification
  val AzurePreviewActivationNotificationType = register(new NotificationType[AzurePreviewActivationNotification] {
    override val format = jsonFormat1(AzurePreviewActivationNotification.apply)
    override val description = "Azure Preview Account Activation"
    override val alwaysOn = true
  })

  case class WorkspaceAddedNotification(recipientUserId: WorkbenchUserId,
                                        accessLevel: String,
                                        workspaceName: WorkspaceName,
                                        workspaceOwnerId: WorkbenchUserId
  ) extends UserNotification
  val WorkspaceAddedNotificationType = register(new NotificationType[WorkspaceAddedNotification] {
    override val format = jsonFormat4(WorkspaceAddedNotification.apply)
    override val description = "Workspace Access Added or Changed"
  })

  case class WorkspaceRemovedNotification(recipientUserId: WorkbenchUserId,
                                          accessLevel: String,
                                          workspaceName: WorkspaceName,
                                          workspaceOwnerId: WorkbenchUserId
  ) extends UserNotification
  val WorkspaceRemovedNotificationType = register(new NotificationType[WorkspaceRemovedNotification] {
    override val format = jsonFormat4(WorkspaceRemovedNotification.apply)
    override val description = "Workspace Access Removed"
  })

  case class WorkspaceInvitedNotification(recipientUserEmail: WorkbenchEmail,
                                          requesterId: WorkbenchUserId,
                                          workspaceName: WorkspaceName,
                                          bucketName: String
  ) extends Notification
  val WorkspaceInvitedNotificationType = register(new NotificationType[WorkspaceInvitedNotification] {
    override val format = jsonFormat4(WorkspaceInvitedNotification.apply)
    override val description = "Invitation"
    override val alwaysOn = true
  })

  case class BillingProjectInvitedNotification(recipientUserEmail: WorkbenchEmail,
                                               requesterId: WorkbenchUserId,
                                               billingProjectName: String
  ) extends Notification

  val BillingProjectInvitedNotificationType = register(new NotificationType[BillingProjectInvitedNotification] {
    override val format = jsonFormat3(BillingProjectInvitedNotification.apply)
    override val description = "Billing Project Invitation"
    override val alwaysOn = true
  })

  case class WorkspaceChangedNotification(recipientUserId: WorkbenchUserId, workspaceName: WorkspaceName)
      extends WorkspaceNotification
  val WorkspaceChangedNotificationType = register(new WorkspaceNotificationType[WorkspaceChangedNotification] {
    override val format = jsonFormat2(WorkspaceChangedNotification.apply)
    override val description = "Workspace changed"
  })

  final case class SuccessfulSubmissionNotification(recipientUserId: WorkbenchUserId,
                                                    workspaceName: WorkspaceName,
                                                    submissionId: String,
                                                    dateSubmitted: String,
                                                    workflowConfiguration: String,
                                                    dataEntity: String,
                                                    workflowCount: Long,
                                                    comment: String
  ) extends WorkspaceNotification
  val SuccessfulSubmissionNotificationType = register(new WorkspaceNotificationType[SuccessfulSubmissionNotification] {
    override val format = jsonFormat8(SuccessfulSubmissionNotification.apply)
    override val description = "Successful submission"
  })

  final case class FailedSubmissionNotification(recipientUserId: WorkbenchUserId,
                                                workspaceName: WorkspaceName,
                                                submissionId: String,
                                                dateSubmitted: String,
                                                workflowConfiguration: String,
                                                dataEntity: String,
                                                workflowCount: Long,
                                                comment: String
  ) extends WorkspaceNotification
  val FailedSubmissionNotificationType = register(new WorkspaceNotificationType[FailedSubmissionNotification] {
    override val format = jsonFormat8(FailedSubmissionNotification.apply)
    override val description = "Failed submission"
  })

  final case class AbortedSubmissionNotification(recipientUserId: WorkbenchUserId,
                                                 workspaceName: WorkspaceName,
                                                 submissionId: String,
                                                 dateSubmitted: String,
                                                 workflowConfiguration: String,
                                                 dataEntity: String,
                                                 workflowCount: Long,
                                                 comment: String
  ) extends WorkspaceNotification
  val AbortedSubmissionNotificationType = register(new WorkspaceNotificationType[AbortedSubmissionNotification] {
    override val format = jsonFormat8(AbortedSubmissionNotification.apply)
    override val description = "Aborted submission"
  })

  case class GroupAccessRequestNotification(recipientUserId: WorkbenchUserId,
                                            groupName: String,
                                            replyToIds: Set[WorkbenchUserId],
                                            requesterId: WorkbenchUserId
  ) extends Notification
  val GroupAccessRequestNotificationType = register(new NotificationType[GroupAccessRequestNotification] {
    override val format = jsonFormat4(GroupAccessRequestNotification)
    override val description = "Group Access Requested"
  })

  // IMPORTANT that this comes after all the calls to register
  val allNotificationTypes: Map[String, NotificationType[_ <: Notification]] = allNotificationTypesBuilder.result()

  implicit object NotificationFormat extends RootJsonFormat[Notification] {

    private val notificationTypeAttribute = "notificationType"

    override def write(obj: Notification): JsValue = {
      val notificationType = obj.getClass.getSimpleName
      val json = obj.toJson(
        allNotificationTypes
          .getOrElse(notificationType, throw new SerializationException(s"format missing for $obj"))
          .format
          .asInstanceOf[RootJsonWriter[Notification]]
      )

      JsObject(json.asJsObject.fields + (notificationTypeAttribute -> JsString(notificationType)))
    }

    override def read(json: JsValue): Notification = json match {
      case JsObject(fields) =>
        val notificationType = fields.getOrElse(
          notificationTypeAttribute,
          throw new DeserializationException(s"missing $notificationTypeAttribute property")
        )
        notificationType match {
          case JsString(tpe) =>
            allNotificationTypes
              .getOrElse(tpe, throw new DeserializationException(s"unrecognized notification type: $tpe"))
              .format
              .read(json)
          case x => throw new DeserializationException(s"unrecognized $notificationTypeAttribute: $x")
        }

      case _ => throw new DeserializationException("unexpected json type")
    }
  }
}
