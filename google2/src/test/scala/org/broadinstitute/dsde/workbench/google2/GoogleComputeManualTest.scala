package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import com.google.cloud.compute.v1.Instance
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

final class GoogleComputeManualTest(pathToCredential: String,
                                    projectStr: String = "broad-dsde-dev",
                                    regionStr: String = "us-central1"
) {

  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = Slf4jLogger.getLogger[IO]

  val blockerBound = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject(projectStr)
  val region = RegionName(regionStr)
  val zone = ZoneName(s"${regionStr}-a")

  val computeServiceResource = GoogleComputeService
    .resource(pathToCredential, blockerBound)

  def callGetCluster(cluster: String): IO[Option[Instance]] =
    computeServiceResource.use { computeService =>
      computeService.getInstance(project, zone, InstanceName(cluster))
    }
}
