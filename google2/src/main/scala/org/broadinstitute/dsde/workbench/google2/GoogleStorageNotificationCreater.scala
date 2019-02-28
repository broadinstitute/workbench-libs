package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Concurrent, Timer}
import com.google.pubsub.v1.ProjectTopicName
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
import org.http4s.client.Client
import org.http4s.client.middleware.{Logger, Retry, RetryPolicy}
import scala.concurrent.duration._

trait GoogleStorageNotificationCreater[F[_]] {
  def createNotification(topic: ProjectTopicName, bucketName: GcsBucketName, filters: Filters): F[Unit]
}

object GoogleStorageNotificationCreater {
  /**
    * This constructor makes assumption that caller wants to enable retry and logging for all http calls.
    * Use `withoutRetryAndLogging` if that's not what you want
    */
  def apply[F[_]: Concurrent: Timer](httpClient: Client[F], config: NotificationCreaterConfig): GoogleStorageNotificationCreater[F] = {
    val retryPolicy = RetryPolicy[F](RetryPolicy.exponentialBackoff(30 seconds, 5))
    val clientWithRetry = Retry(retryPolicy)(httpClient)
    val clientWithRetryAndLogging = Logger(logHeaders = true, logBody = true)(clientWithRetry)
    new GoogleStorageNotificationCreatorInterpreter[F](clientWithRetryAndLogging, config)
  }

  def withoutRetryAndLogging[F[_]: Concurrent: Timer](httpClient: Client[F], config: NotificationCreaterConfig): GoogleStorageNotificationCreater[F] =
    new GoogleStorageNotificationCreatorInterpreter[F](httpClient, config)
}
