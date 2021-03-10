package org.broadinstitute.dsde.workbench.google2

import java.util.UUID

import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, IO}
import cats.mtl.Ask
import com.google.cloud.compute.v1.Operation
import com.google.cloud.dataproc.v1.Cluster
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}

final class GoogleDataprocManualTest(pathToCredential: String,
                                     projectStr: String = "broad-dsde-dev",
                                     regionStr: String = "us-central1"
) {

  import scala.concurrent.ExecutionContext.global

  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = new ConsoleLogger("dataproc-manual-test", LogLevel(true, true, true, true))

  val blocker = Blocker.liftExecutionContext(global)
  val blockerBound = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject(projectStr)
  val region = RegionName(regionStr)
  val zone = ZoneName(s"${regionStr}-a")

  val dataprocServiceResource = GoogleComputeService
    .resource(pathToCredential, blocker, blockerBound)
    .flatMap(computeService =>
      GoogleDataprocService.resource(computeService, pathToCredential, blocker, blockerBound, region)
    )

  def callStopCluster(cluster: String): IO[List[Operation]] =
    dataprocServiceResource.use { dataprocService =>
      dataprocService.stopCluster(project, region, DataprocClusterName(cluster), metadata = None)
    }

  def callResizeCluster(cluster: String,
                        numWorkers: Option[Int],
                        numPreemptibles: Option[Int]
  ): IO[Option[DataprocOperation]] =
    dataprocServiceResource.use { dataprocService =>
      dataprocService.resizeCluster(project, region, DataprocClusterName(cluster), numWorkers, numPreemptibles)
    }

  def callGetCluster(cluster: String): IO[Option[Cluster]] =
    dataprocServiceResource.use { dataprocService =>
      dataprocService.getCluster(project, region, DataprocClusterName(cluster))
    }

  def callGetClusterInstances(cluster: String): IO[Map[DataprocRoleZonePreemptibility, Set[InstanceName]]] =
    dataprocServiceResource.use { dataprocService =>
      dataprocService.getClusterInstances(project, region, DataprocClusterName(cluster))
    }
}
