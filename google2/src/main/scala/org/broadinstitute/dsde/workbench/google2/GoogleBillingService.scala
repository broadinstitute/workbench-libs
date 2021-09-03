package org.broadinstitute.dsde.workbench.google2

import cats.effect.std.Semaphore
import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.billing.v1.{CloudBillingClient, CloudBillingSettings, ProjectBillingInfo}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import java.nio.file.Path

trait GoogleBillingService[F[_]] {
  def getBillingInfo(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[ProjectBillingInfo]]
  def isBillingEnabled(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Boolean]
}

object GoogleBillingService {

  def resource[F[_]: StructuredLogger: Async](
    pathToCredential: Path,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleBillingService[F]] =
    for {
      credential <- credentialResource(pathToCredential.toString)
      scopedCredential = credential.createScoped(CLOUD_PLATFORM_SCOPE)
      interpreter <- fromCredential(scopedCredential, blockerBound)
    } yield interpreter

  def fromCredential[F[_]: StructuredLogger: Async](
    googleCredentials: GoogleCredentials,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleBillingService[F]] = {
    val credentialsProvider = FixedCredentialsProvider.create(googleCredentials)

    val billingSettings = CloudBillingSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()

    for {
      billingClient <- backgroundResourceF(CloudBillingClient.create(billingSettings))
    } yield new GoogleBillingInterpreter[F](billingClient, blockerBound)
  }
}
