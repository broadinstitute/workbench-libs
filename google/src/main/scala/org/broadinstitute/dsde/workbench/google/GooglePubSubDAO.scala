package org.broadinstitute.dsde.workbench.google

import com.google.api.services.pubsub.model.Topic
import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchException}
import org.broadinstitute.dsde.workbench.google.GooglePubSubDAO._

import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by dvoet on 12/7/16.
 */
object GooglePubSubDAO {
  sealed trait AckStatus

  case object MessageAcknowledged extends AckStatus
  case object MessageNotAcknowledged extends AckStatus

  sealed trait HandledStatus

  case object MessageHandled extends HandledStatus
  case object MessageNotHandled extends HandledStatus
  case object NoMessage extends HandledStatus

  case class PubSubMessage(ackId: String, contents: String, attributes: Map[String, String] = Map.empty)
  case class MessageRequest(text: String, attributes: Map[String, String] = Map.empty)
}

trait GooglePubSubDAO {
  implicit val executionContext: ExecutionContext

  def createTopic(topicName: String): Future[Boolean]

  def deleteTopic(topicName: String): Future[Boolean]

  def getTopic(topicName: String)(implicit executionContext: ExecutionContext): Future[Option[Topic]]

  def setTopicIamPermissions(topicName: String, permissions: Map[WorkbenchEmail, String]): Future[Unit]

  def createSubscription(topicName: String, subscriptionName: String, ackDeadlineSeconds: Option[Int]): Future[Boolean]

  def deleteSubscription(subscriptionName: String): Future[Boolean]

  def publishMessages(topicName: String, messages: scala.collection.Seq[MessageRequest]): Future[Unit]

  def acknowledgeMessages(subscriptionName: String, messages: scala.collection.Seq[PubSubMessage]): Future[Unit]

  def acknowledgeMessagesById(subscriptionName: String, ackIds: scala.collection.Seq[String]): Future[Unit]

  def extendDeadline(subscriptionName: String,
                     messages: scala.collection.Seq[PubSubMessage],
                     extendDeadlineBySeconds: Int
  ): Future[Unit]

  def extendDeadlineById(subscriptionName: String,
                         ackIds: scala.collection.Seq[String],
                         extendDeadlineBySeconds: Int
  ): Future[Unit]

  def pullMessages(subscriptionName: String, maxMessages: Int): Future[scala.collection.Seq[PubSubMessage]]

  def withMessage(subscriptionName: String)(op: (String) => Future[AckStatus]): Future[HandledStatus] =
    withMessages(subscriptionName, 1) {
      case scala.collection.Seq(msg) => op(msg)
      case _ => throw new WorkbenchException(s"Unable to process message from subscription $subscriptionName")
    }

  def withMessages(subscriptionName: String, maxMessages: Int)(
    op: (scala.collection.Seq[String]) => Future[AckStatus]
  ): Future[HandledStatus] =
    pullMessages(subscriptionName, maxMessages) flatMap {
      case scala.collection.Seq() => Future.successful(NoMessage)
      case messages =>
        op(messages.map(msg => msg.contents)) flatMap {
          case MessageAcknowledged    => acknowledgeMessages(subscriptionName, messages).map(_ => MessageHandled)
          case MessageNotAcknowledged => Future.successful(MessageNotHandled)
        }
    }
}
