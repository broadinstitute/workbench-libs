package org.broadinstitute.dsde.workbench.google2

import java.util.UUID
import java.nio.file.Paths

import cats.effect.IO
import cats.mtl.Ask
import com.google.cloud.resourcemanager.Project
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}
import cats.effect.std.Semaphore

class GoogleResourceManualTest(pathToCredential: String) {
  import scala.concurrent.ExecutionContext.global

  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = new ConsoleLogger("resource-manual-test", LogLevel(true, true, true, true))

  val blocker = Blocker.liftExecutionContext(global)
  val blockerBound = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject("broad-dsde-dev")

  val credPath = Paths.get(pathToCredential)

  val resourceService = GoogleResourceService.resource(credPath, blocker, blockerBound)

  def callGetProject(project: GoogleProject = project): IO[Option[Project]] =
    resourceService.use { b =>
      b.getProject(project)
    }

}
