package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}

import java.nio.file.Paths
import java.util.UUID

class GoogleBillingManualTest(pathToCredential: String, projectStr: String = "broad-dsde-dev") {

  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = new ConsoleLogger("billing-manual-test", LogLevel(true, true, true, true))

  val blockerBound = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject(projectStr)
  val credPath = Paths.get(pathToCredential)

  val billingServiceResource = GoogleBillingService.resource(credPath, blockerBound)

  def callIsBillingEnabled(): IO[Boolean] =
    billingServiceResource.use { b =>
      b.isBillingEnabled(project)
    }

}
