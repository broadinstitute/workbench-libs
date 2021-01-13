package org.broadinstitute.dsde.workbench.google2

import java.util.UUID
import java.nio.file.Paths

import cats.effect.{Blocker, IO}
import cats.effect.concurrent.Semaphore
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}

class GoogleBillingManualTest(pathToCredential: String, projectStr: String = "broad-dsde-dev") {
  import scala.concurrent.ExecutionContext.global

  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = new ConsoleLogger("billing-manual-test", LogLevel(true, true, true, true))

  val blocker = Blocker.liftExecutionContext(global)
  val blockerBound = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject(projectStr)
  val credPath = Paths.get(pathToCredential)

  val billingServiceResource = GoogleBillingService.resource(credPath, blocker, blockerBound)

  def callIsBillingEnabled(): IO[Boolean] =
    billingServiceResource.use { b =>
      b.isBillingEnabled(project)
    }

}
