package org.broadinstitute.dsde.workbench.dataaccess

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.google.GooglePubSubDAO
import org.broadinstitute.dsde.workbench.google.GooglePubSubDAO.MessageRequest
import org.broadinstitute.dsde.workbench.model.Notifications.{Notification, NotificationFormat}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

trait NotificationDAO extends LazyLogging {
  def fireAndForgetNotification(notification: Notification)(implicit executionContext: ExecutionContext): Unit =
    fireAndForgetNotifications(Seq(notification))

  def fireAndForgetNotifications[T <: Notification](
    notifications: Traversable[T]
  )(implicit executionContext: ExecutionContext): Unit =
    sendNotifications(notifications.map(NotificationFormat.write(_).compactPrint)).onComplete {
      case Failure(t)            => logger.error("failure sending notifications: " + notifications, t)
      case scala.util.Success(_) => ()
    }

  protected def sendNotifications(notification: Traversable[String]): Future[Unit]
}

class PubSubNotificationDAO(googlePubSubDAO: GooglePubSubDAO, topicName: String) extends NotificationDAO {
  import scala.concurrent.ExecutionContext.Implicits.global
  // attempt to create the topic, if it already exists this will log a message and then move on
  googlePubSubDAO.createTopic(topicName).map { created =>
    if (!created) logger.info(s"The topic $topicName was not created because it already exists.")
  }

  protected def sendNotifications(notification: Traversable[String]): Future[Unit] =
    googlePubSubDAO.publishMessages(topicName, notification.toList.map(MessageRequest(_)))
}
