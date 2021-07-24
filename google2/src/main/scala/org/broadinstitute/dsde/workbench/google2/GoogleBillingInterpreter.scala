package org.broadinstitute.dsde.workbench.google2

import cats.Parallel
import cats.effect.Async
import cats.effect.std.Semaphore
import cats.mtl.Ask
import cats.syntax.all._
import com.google.cloud.billing.v1.{CloudBillingClient, ProjectBillingInfo}
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

private[google2] class GoogleBillingInterpreter[F[_]: StructuredLogger: Parallel](
  billingClient: CloudBillingClient,
  blockerBound: Semaphore[F]
)(implicit F: Async[F])
    extends GoogleBillingService[F] {

  override def getBillingInfo(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[ProjectBillingInfo]] =
    for {
      info <- tracedLogging(
        blockerBound.permit.use(_ =>
            recoverF(F.blocking(billingClient.getProjectBillingInfo(s"projects/${project.value}")), whenStatusCode(404))
        ),
        s"com.google.cloud.billing.v1.CloudBillingClient.getProjectBillingInfo(${project.value})",
        showBillingInfo
      )
    } yield info

  override def isBillingEnabled(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Boolean] =
    for {
      info <- getBillingInfo(project)
    } yield info.map(_.getBillingEnabled).getOrElse(false)
}
