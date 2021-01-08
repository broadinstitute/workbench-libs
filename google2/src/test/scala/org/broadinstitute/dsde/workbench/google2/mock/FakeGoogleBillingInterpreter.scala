package org.broadinstitute.dsde.workbench.google2.mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.cloud.billing.v1.ProjectBillingInfo
import org.broadinstitute.dsde.workbench.google2.GoogleBillingService
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class FakeGoogleBillingInterpreter extends GoogleBillingService[IO] {
  override def getBillingInfo(project: GoogleProject)(implicit ev: Ask[IO, TraceId]): IO[Option[ProjectBillingInfo]] = IO(
    Some(ProjectBillingInfo.newBuilder().build())
  )

  override def isBillingEnabled(project: GoogleProject)(implicit ev: Ask[IO, TraceId]): IO[Boolean] = IO.pure(true)
}

object FakeGoogleBillingInterpreter extends FakeGoogleBillingInterpreter
