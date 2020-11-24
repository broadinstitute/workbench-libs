package org.broadinstitute.dsde.workbench.google

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.http.HttpResponseException
import com.google.api.services.pubsub.model._
import com.google.api.services.pubsub.{Pubsub, PubsubScopes}
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GooglePubSubDAO._
import org.broadinstitute.dsde.workbench.google.GoogleUtilities.RetryPredicates._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.isServiceAccount

import scala.collection.JavaConverters._
import scala.concurrent._

/**
 * Created by mbemis on 5/6/16.
 */
class HttpGooglePubSubDAO(appName: String,
                          googleCredentialMode: GoogleCredentialMode,
                          workbenchMetricBaseName: String,
                          serviceProject: String
)(implicit system: ActorSystem, executionContext: ExecutionContext)
    extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName)
    with GooglePubSubDAO {

  @deprecated(
    message =
      "This way of instantiating HttpGooglePubSubDAO has been deprecated. Please update to use the primary constructor.",
    since = "0.15"
  )
  def this(clientEmail: String,
           pemFile: String,
           appName: String,
           serviceProject: String,
           workbenchMetricBaseName: String
  )(implicit system: ActorSystem, executionContext: ExecutionContext) = {
    this(appName, Pem(WorkbenchEmail(clientEmail), new File(pemFile)), workbenchMetricBaseName, serviceProject)
  }

  override val scopes = Seq(PubsubScopes.PUBSUB)

  implicit override val service = GoogleInstrumentedService.PubSub

  private val characterEncoding = "UTF-8"

  private lazy val pubSub =
    new Pubsub.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()

  override def createTopic(topicName: String) =
    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(pubSub.projects().topics().create(topicToFullPath(topicName), new Topic()))
        true
    } {
      case t: HttpResponseException if t.getStatusCode == 409 => false
    }

  override def deleteTopic(topicName: String): Future[Boolean] =
    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(pubSub.projects().topics().delete(topicToFullPath(topicName)))
        true
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => false
    }

  override def getTopic(topicName: String)(implicit executionContext: ExecutionContext): Future[Option[Topic]] =
    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        Option(executeGoogleRequest(pubSub.projects().topics().get(topicToFullPath(topicName))))
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
    }

  override def setTopicIamPermissions(topicName: String, permissions: Map[WorkbenchEmail, String]): Future[Unit] = {
    val bindings = permissions.map { case (userEmail, role) =>
      val memberType = if (isServiceAccount(userEmail)) "serviceAccount" else "user"
      val email = s"$memberType:${userEmail.value}"

      new Binding().setMembers(List(email).asJava).setRole(role)
    }

    val request = new SetIamPolicyRequest().setPolicy(new Policy().setBindings(bindings.toList.asJava))

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(pubSub.projects().topics().setIamPolicy(topicToFullPath(topicName), request))
    }
  }

  override def createSubscription(topicName: String, subscriptionName: String) =
    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        val subscription = new Subscription().setTopic(topicToFullPath(topicName))
        executeGoogleRequest(
          pubSub.projects().subscriptions().create(subscriptionToFullPath(subscriptionName), subscription)
        )
        true
    } {
      case t: HttpResponseException if t.getStatusCode == 409 => false
    }

  override def deleteSubscription(subscriptionName: String): Future[Boolean] =
    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(pubSub.projects().subscriptions().delete(subscriptionToFullPath(subscriptionName)))
        true
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => false
    }

  override def publishMessages(topicName: String, messages: scala.collection.Seq[String]) = {
    logger.debug(s"publishing to google pubsub topic $topicName, messages [${messages.mkString(", ")}]")
    Future
      .traverse(messages.grouped(1000)) { messageBatch =>
        retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
          val pubsubMessages =
            messageBatch.map(text => new PubsubMessage().encodeData(text.getBytes(characterEncoding)))
          val pubsubRequest = new PublishRequest().setMessages(pubsubMessages.asJava)
          executeGoogleRequest(pubSub.projects().topics().publish(topicToFullPath(topicName), pubsubRequest))
        }
      }
      .map(_ => ())
  }

  override def acknowledgeMessages(subscriptionName: String, messages: scala.collection.Seq[PubSubMessage]) =
    acknowledgeMessagesById(subscriptionName, messages.map(_.ackId))

  override def acknowledgeMessagesById(subscriptionName: String, ackIds: scala.collection.Seq[String]) =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      val ackRequest = new AcknowledgeRequest().setAckIds(ackIds.asJava)
      executeGoogleRequest(
        pubSub.projects().subscriptions().acknowledge(subscriptionToFullPath(subscriptionName), ackRequest)
      )
    }

  override def pullMessages(subscriptionName: String, maxMessages: Int): Future[scala.collection.Seq[PubSubMessage]] =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      val pullRequest = new PullRequest()
        .setReturnImmediately(true)
        .setMaxMessages(maxMessages) //won't keep the connection open if there's no msgs available
      val messages = executeGoogleRequest(
        pubSub.projects().subscriptions().pull(subscriptionToFullPath(subscriptionName), pullRequest)
      ).getReceivedMessages.asScala
      if (messages == null)
        Seq.empty
      else
        messages.toSeq.map(message =>
          PubSubMessage(message.getAckId, new String(message.getMessage.decodeData(), characterEncoding))
        )
    }

  def topicToFullPath(topicName: String) = s"projects/${serviceProject}/topics/${topicName}"
  def subscriptionToFullPath(subscriptionName: String) = s"projects/${serviceProject}/subscriptions/${subscriptionName}"
}
