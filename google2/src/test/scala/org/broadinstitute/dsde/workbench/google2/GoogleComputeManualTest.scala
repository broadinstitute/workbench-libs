package org.broadinstitute.dsde.workbench.google2

import java.util.UUID

import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, IO}
import cats.mtl.Ask
import com.google.cloud.compute.v1.Instance
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

final class GoogleComputeManualTest(pathToCredential: String,
                                    projectStr: String = "broad-dsde-dev",
                                    regionStr: String = "us-central1"
) {

  import scala.concurrent.ExecutionContext.global

  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = Slf4jLogger.getLogger[IO]

  val blocker = Blocker.liftExecutionContext(global)
  val blockerBound = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject(projectStr)
  val region = RegionName(regionStr)
  val zone = ZoneName(s"${regionStr}-a")

  val computeServiceResource = GoogleComputeService
    .resource(pathToCredential, blocker, blockerBound)

  def callGetCluster(cluster: String): IO[Option[Instance]] =
    computeServiceResource.use { computeService =>
      computeService.getInstance(project, zone, InstanceName(cluster))
    }
}
