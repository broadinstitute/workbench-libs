package org.broadinstitute.dsde.workbench.dataaccess

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.google.GooglePubSubDAO
import org.broadinstitute.dsde.workbench.model.Notifications.{Notification, NotificationFormat}

import scala.concurrent.{ExecutionContext, Future}

trait NotificationDAO extends LazyLogging {
  def fireAndForgetNotification(notification: Notification)(implicit executionContext: ExecutionContext): Unit = fireAndForgetNotifications(Seq(notification))

  def fireAndForgetNotifications[T <: Notification](notifications: Traversable[T])(implicit executionContext: ExecutionContext): Unit = {
    sendNotifications(notifications.map(NotificationFormat.write(_).compactPrint)).onFailure {
      case t: Throwable => logger.error("failure sending notifications: " + notifications, t)
    }
  }

  protected def sendNotifications(notification: Traversable[String]): Future[Unit]
}

class PubSubNotificationDAO(googlePubSubDAO: GooglePubSubDAO, topicName: String) extends NotificationDAO {
  // attempt to create the topic, if it already exists this will fail but who cares
  googlePubSubDAO.createTopic(topicName).map { result =>
    if(!result) logger.info(s"The topic $topicName was not created because it already exists.")
  }

  protected def sendNotifications(notification: Traversable[String]): Future[Unit] = {
    googlePubSubDAO.publishMessages(topicName, notification.toSeq)
  }
}
