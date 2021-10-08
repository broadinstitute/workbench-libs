package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import com.google.cloud.compute.v1.Operation
import com.google.cloud.dataproc.v1.Cluster
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}

import java.util.UUID

final class GoogleDataprocManualTest(pathToCredential: String,
                                     projectStr: String = "broad-dsde-dev",
                                     regionStr: String = "us-central1"
) {

  implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = new ConsoleLogger("dataproc-manual-test", LogLevel(true, true, true, true))

  val blockerBound = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject(projectStr)
  val region = RegionName(regionStr)
  val zone = ZoneName(s"$regionStr-a")

  val dataprocServiceResource = GoogleComputeService
    .resource(pathToCredential, blockerBound)
    .flatMap(computeService =>
      GoogleDataprocService.resource(computeService, pathToCredential, blockerBound, Set(region))
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
