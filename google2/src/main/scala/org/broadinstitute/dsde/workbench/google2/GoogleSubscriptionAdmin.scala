package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1.{SubscriptionAdminClient, SubscriptionAdminSettings}
import com.google.pubsub.v1.{ProjectSubscriptionName, Subscription}
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import java.nio.file.Path

trait GoogleSubscriptionAdmin[F[_]] {
  def list(project: GoogleProject)(implicit ev: Ask[F, TraceId]): Stream[F, Subscription]
  def delete(projectSubscriptionName: ProjectSubscriptionName)(implicit ev: Ask[F, TraceId]): F[Unit]
}

object GoogleSubscriptionAdmin {
  def fromCredentialPath[F[_]: StructuredLogger: Async](
    pathToCredential: Path
  ): Resource[F, GoogleSubscriptionAdmin[F]] =
    for {
      credential <- credentialResource(pathToCredential.toString)
      topicAdmin <- fromServiceAccountCredential(credential)
    } yield topicAdmin

  def fromServiceAccountCredential[F[_]: StructuredLogger: Async](
    serviceAccountCredentials: ServiceAccountCredentials
  ): Resource[F, GoogleSubscriptionAdmin[F]] =
    for {
      client <- Resource.make(
        Async[F].delay(
          SubscriptionAdminClient.create(
            SubscriptionAdminSettings
              .newBuilder()
              .setCredentialsProvider(FixedCredentialsProvider.create(serviceAccountCredentials))
              .build()
          )
        )
      )(client => Async[F].delay(client.shutdown()))
    } yield new GoogleSubscriptionAdminInterpreter[F](client)
}
