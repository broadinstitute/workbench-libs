package org.broadinstitute.dsde.workbench.google2

import java.nio.file.Path

import cats.effect.{Resource, Sync, Timer}
import cats.mtl.Ask
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1.{SubscriptionAdminClient, SubscriptionAdminSettings}
import com.google.pubsub.v1.{ProjectSubscriptionName, Subscription}
import fs2.Stream
import org.typelevel.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

trait GoogleSubscriptionAdmin[F[_]] {
  def list(project: GoogleProject)(implicit ev: Ask[F, TraceId]): Stream[F, Subscription]
  def delete(projectSubscriptionName: ProjectSubscriptionName)(implicit ev: Ask[F, TraceId]): F[Unit]
}

object GoogleSubscriptionAdmin {
  def fromCredentialPath[F[_]: StructuredLogger: Sync: Timer](
    pathToCredential: Path
  ): Resource[F, GoogleSubscriptionAdmin[F]] =
    for {
      credential <- credentialResource(pathToCredential.toString)
      topicAdmin <- fromServiceAccountCredential(credential)
    } yield topicAdmin

  def fromServiceAccountCredential[F[_]: StructuredLogger: Sync: Timer](
    serviceAccountCredentials: ServiceAccountCredentials
  ): Resource[F, GoogleSubscriptionAdmin[F]] =
    for {
      client <- Resource.make(
        Sync[F].delay(
          SubscriptionAdminClient.create(
            SubscriptionAdminSettings
              .newBuilder()
              .setCredentialsProvider(FixedCredentialsProvider.create(serviceAccountCredentials))
              .build()
          )
        )
      )(client => Sync[F].delay(client.shutdown()))
    } yield new GoogleSubscriptionAdminInterpreter[F](client)
}
