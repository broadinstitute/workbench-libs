package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import com.google.cloud.resourcemanager.Project
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}

import java.nio.file.Paths
import java.util.UUID

class GoogleResourceManualTest(pathToCredential: String) {

  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = new ConsoleLogger("resource-manual-test", LogLevel(true, true, true, true))

  val blockerBound = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject("broad-dsde-dev")

  val credPath = Paths.get(pathToCredential)

  val resourceService = GoogleResourceService.resource(credPath, blockerBound)

  def callGetProject(project: GoogleProject = project): IO[Option[Project]] =
    resourceService.use { b =>
      b.getProject(project)
    }

}
