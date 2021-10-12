package org.broadinstitute.dsde.workbench.google2
package mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.cloud.compute.v1.Operation
import com.google.cloud.dataproc.v1.{Cluster, ClusterOperationMetadata}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class BaseFakeGoogleDataprocService extends GoogleDataprocService[IO] {
  override def createCluster(
    project: GoogleProject,
    region: RegionName,
    clusterName: DataprocClusterName,
    createClusterConfig: Option[CreateClusterConfig]
  )(implicit ev: Ask[IO, TraceId]): IO[Option[DataprocOperation]] =
    IO.pure(
      Some(
        DataprocOperation(OperationName("opName"),
                          ClusterOperationMetadata.newBuilder().setClusterUuid("clusterUuid").build
        )
      )
    )

  override def stopCluster(project: GoogleProject,
                           region: RegionName,
                           clusterName: DataprocClusterName,
                           metadata: Option[Map[String, String]] = None
  )(implicit
    ev: Ask[IO, TraceId]
  ): IO[DataprocOperation] = IO.pure(
    DataprocOperation(OperationName("opName"),
                      ClusterOperationMetadata.newBuilder().setClusterUuid("clusterUuid").build
    )
  )

  def startCluster(project: GoogleProject,
                   region: RegionName,
                   clusterName: DataprocClusterName,
                   numPreemptibles: Option[Int],
                   metadata: Option[Map[String, String]]
  )(implicit
    ev: Ask[IO, TraceId]
  ): IO[List[Operation]] = IO.pure(List.empty[Operation])

  override def resizeCluster(project: GoogleProject,
                             region: RegionName,
                             clusterName: DataprocClusterName,
                             numWorkers: Option[Int] = None,
                             numPreemptibles: Option[Int] = None
  )(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[DataprocOperation]] = IO.pure(
    Some(
      DataprocOperation(OperationName("opName"),
                        ClusterOperationMetadata.newBuilder().setClusterUuid("clusterUuid").build
      )
    )
  )

  override def deleteCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[DataprocOperation]] = IO.pure(
    Some(
      DataprocOperation(OperationName("opName"),
                        ClusterOperationMetadata.newBuilder().setClusterUuid("clusterUuid").build
      )
    )
  )

  override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Cluster]] = IO.pure(None)

  override def getClusterInstances(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
    implicit ev: Ask[IO, TraceId]
  ): IO[Map[DataprocRoleZonePreemptibility, Set[InstanceName]]] = IO.pure(Map.empty)

  override def getClusterError(region: RegionName, operationName: OperationName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[ClusterError]] = IO.pure(None)
}

object FakeGoogleDataprocService extends BaseFakeGoogleDataprocService
