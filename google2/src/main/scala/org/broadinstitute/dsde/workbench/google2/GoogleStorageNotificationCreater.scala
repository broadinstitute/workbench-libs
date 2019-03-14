package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Concurrent, Resource, Timer}
import com.google.pubsub.v1.ProjectTopicName
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.GoogleStorageNotificationCreatorInterpreter.credentialResourceWithScope
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
import org.http4s.client.Client
import org.http4s.client.middleware.{Retry, RetryPolicy, Logger => Http4sLogger}

import scala.concurrent.duration._

trait GoogleStorageNotificationCreater[F[_]] {
  def createNotification(topic: ProjectTopicName, bucketName: GcsBucketName, filters: Filters, traceId: Option[TraceId]): F[Unit]
}

object GoogleStorageNotificationCreater {
  /**
    * This constructor makes assumption that caller wants to enable retry and logging for all http calls.
    * Use `withoutRetryAndLogging` if that's not what you want
    */
  def withRetryAndLogging[F[_]: Concurrent: Timer: Logger](httpClient: Client[F], config: NotificationCreaterConfig): Resource[F, GoogleStorageNotificationCreater[F]] = {
    val retryPolicy = RetryPolicy[F](RetryPolicy.exponentialBackoff(30 seconds, 5))
    val clientWithRetry = Retry(retryPolicy)(httpClient)
    val clientWithRetryAndLogging = Http4sLogger(logHeaders = true, logBody = true)(clientWithRetry)
    for {
      credentials <- credentialResourceWithScope(config.pathToCredentialJson)
    } yield new GoogleStorageNotificationCreatorInterpreter[F](clientWithRetryAndLogging, config, credentials)
  }

  def withoutRetryAndLogging[F[_]: Concurrent: Timer: Logger](httpClient: Client[F], config: NotificationCreaterConfig): Resource[F, GoogleStorageNotificationCreater[F]] =
    for {
      credentials <- credentialResourceWithScope(config.pathToCredentialJson)
    } yield new GoogleStorageNotificationCreatorInterpreter[F](httpClient, config, credentials)
}
