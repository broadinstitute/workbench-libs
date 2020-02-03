package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Concurrent, Resource, Timer}
import com.google.pubsub.v1.ProjectTopicName
import com.google.cloud.Identity
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.GoogleServiceHttpInterpreter.credentialResourceWithScope
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}
import org.http4s.client.Client
import org.http4s.client.middleware.{Retry, RetryPolicy, Logger => Http4sLogger}

import scala.concurrent.duration._

// This class provides functions only exposed via rest APIs
trait GoogleServiceHttp[F[_]] {
  def createNotification(topic: ProjectTopicName,
                         bucketName: GcsBucketName,
                         filters: Filters,
                         traceId: Option[TraceId]): F[Unit]
  def getProjectServiceAccount(project: GoogleProject, traceId: Option[TraceId]): F[Identity]
}

object GoogleServiceHttp {
  def withRetryAndLogging[F[_]: Concurrent: Timer: Logger](
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

  def withoutRetryAndLogging[F[_]: Concurrent: Timer: Logger](
    httpClient: Client[F],
    config: NotificationCreaterConfig
  ): Resource[F, GoogleServiceHttp[F]] =
    for {
      credentials <- credentialResourceWithScope(config.pathToCredentialJson)
    } yield new GoogleServiceHttpInterpreter[F](httpClient, config, credentials)
}
