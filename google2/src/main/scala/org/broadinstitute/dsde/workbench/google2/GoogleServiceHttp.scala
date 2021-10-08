package org.broadinstitute.dsde.workbench
package google2

import cats.effect.{Async, Resource, Temporal}
import com.google.cloud.Identity
import com.google.pubsub.v1.TopicName
import org.broadinstitute.dsde.workbench.google2.GoogleServiceHttpInterpreter.credentialResourceWithScope
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}
import org.http4s.client.Client
import org.http4s.client.middleware.{Logger => Http4sLogger, Retry, RetryPolicy}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._

// This class provides functions only exposed via rest APIs
trait GoogleServiceHttp[F[_]] {
  def createNotification(topic: TopicName,
                         bucketName: GcsBucketName,
                         filters: Filters,
                         traceId: Option[TraceId]
  ): F[Unit]
  def getProjectServiceAccount(project: GoogleProject, traceId: Option[TraceId]): F[Identity]
}

object GoogleServiceHttp {
  def withRetryAndLogging[F[_]: Temporal: Async: Logger](
    httpClient: Client[F],
    config: NotificationCreaterConfig
  ): Resource[F, GoogleServiceHttp[F]] = {
    val retryPolicy = RetryPolicy[F](RetryPolicy.exponentialBackoff(30 seconds, 5))
    val clientWithRetry = Retry(retryPolicy)(httpClient)
    val clientWithRetryAndLogging = Http4sLogger(logHeaders = true, logBody = true)(clientWithRetry)
    for {
      credentials <- credentialResourceWithScope(config.pathToCredentialJson)
    } yield new GoogleServiceHttpInterpreter[F](clientWithRetryAndLogging, config, credentials)
  }

  def withoutRetryAndLogging[F[_]: Temporal: Async: Logger](
    httpClient: Client[F],
    config: NotificationCreaterConfig
  ): Resource[F, GoogleServiceHttp[F]] =
    for {
      credentials <- credentialResourceWithScope(config.pathToCredentialJson)
    } yield new GoogleServiceHttpInterpreter[F](httpClient, config, credentials)
}
