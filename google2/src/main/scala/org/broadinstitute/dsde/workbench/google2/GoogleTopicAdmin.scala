package org.broadinstitute.dsde.workbench
package google2

import cats.effect.{Resource, Sync, Timer}
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.Identity
import com.google.pubsub.v1.TopicName
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.google2.GoogleTopicAdminInterpreter._
import org.broadinstitute.dsde.workbench.model.TraceId

trait GoogleTopicAdmin[F[_]] {

  /**
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def create(projectTopicName: TopicName, traceId: Option[TraceId] = None): Stream[F, Unit]

  def delete(projectTopicName: TopicName, traceId: Option[TraceId] = None): Stream[F, Unit]

  /**
   * @param projectTopicName
   * @param members can have the following values
   * * `allUsers`: A special identifier that represents anyone who is
   *    on the internet; with or without a Google account.
   * * `allAuthenticatedUsers`: A special identifier that represents anyone
   *    who is authenticated with a Google account or a service account.
   * * `user:{emailid}`: An email address that represents a specific Google
   *    account. For example, `alice&#64;gmail.com` or `joe&#64;example.com`.
   * * `serviceAccount:{emailid}`: An email address that represents a service
   *    account. For example, `my-other-app&#64;appspot.gserviceaccount.com`.
   * * `group:{emailid}`: An email address that represents a Google group.
   *    For example, `admins&#64;example.com`.
   * * `domain:{domain}`: A Google Apps domain name that represents all the
   *    users of that domain. For example, `google.com` or `example.com`.
   * @param traceId uuid for tracing a unique call flow in logging
   */
  def createWithPublisherMembers(projectTopicName: TopicName,
                                 members: List[Identity],
                                 traceId: Option[TraceId] = None): Stream[F, Unit]
}

object GoogleTopicAdmin {
  def fromCredentialPath[F[_]: StructuredLogger: Sync: Timer](
    pathToCredential: String,
    retryConfig: RetryConfig = GoogleTopicAdminInterpreter.defaultRetryConfig
  ): Resource[F, GoogleTopicAdmin[F]] =
    for {
      credential <- credentialResource(pathToCredential)
      topicAdmin <- fromServiceAccountCrendential(credential, retryConfig)
    } yield topicAdmin

  def fromServiceAccountCrendential[F[_]: StructuredLogger: Sync: Timer](
    serviceAccountCredentials: ServiceAccountCredentials,
    retryConfig: RetryConfig
  ): Resource[F, GoogleTopicAdmin[F]] =
    for {
      topicAdminClient <- topicAdminClientResource(serviceAccountCredentials)
    } yield new GoogleTopicAdminInterpreter[F](topicAdminClient, retryConfig)
}
