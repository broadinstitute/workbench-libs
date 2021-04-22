package org.broadinstitute.dsde.workbench.google2

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Resource, Timer}
import cats.mtl.Ask
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.services.compute.ComputeScopes
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.billing.v1.{CloudBillingClient, CloudBillingSettings, ProjectBillingInfo}
import org.typelevel.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
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

  def resource[F[_]: StructuredLogger: Async: Parallel: Timer: ContextShift](
    pathToCredential: Path,
    blocker: Blocker,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleBillingService[F]] =
    for {
      credential <- credentialResource(pathToCredential.toString)
      scopedCredential = credential.createScoped(ComputeScopes.CLOUD_PLATFORM)
      interpreter <- fromCredential(scopedCredential, blocker, blockerBound)
    } yield interpreter

  def fromCredential[F[_]: StructuredLogger: Async: Parallel: Timer: ContextShift](
    googleCredentials: GoogleCredentials,
    blocker: Blocker,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleBillingService[F]] = {
    val credentialsProvider = FixedCredentialsProvider.create(googleCredentials)

    val billingSettings = CloudBillingSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()

    for {
      billingClient <- backgroundResourceF(CloudBillingClient.create(billingSettings))
    } yield new GoogleBillingInterpreter[F](billingClient, blocker, blockerBound)
  }
}
