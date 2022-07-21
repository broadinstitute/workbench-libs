package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import com.google.api.gax.longrunning.OperationFuture
import com.google.cloud.compute.v1.Operation
import com.google.cloud.dataproc.v1.{Cluster, ClusterOperationMetadata}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, InstanceName, LogLevel}

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

  val dataprocServiceResource = for {
    computeService <- GoogleComputeService
      .resource(pathToCredential, blockerBound)
    res <- GoogleDataprocService.resource(computeService, pathToCredential, blockerBound, Set(region))
  } yield res

  def callStopCluster(cluster: String): IO[Option[OperationFuture[Cluster, ClusterOperationMetadata]]] =
    dataprocServiceResource.use { dataprocService =>
      dataprocService.stopCluster(project, region, DataprocClusterName(cluster), metadata = None, true)
    }

  def callResizeCluster(cluster: String,
                        numWorkers: Option[Int],
                        numPreemptibles: Option[Int]
  ): IO[Option[OperationFuture[Cluster, ClusterOperationMetadata]]] =
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
