package org.broadinstitute.dsde.workbench.google2
package mock

import cats.implicits._
import cats.effect.IO
import cats.mtl.ApplicativeAsk
import com.google.cloud.dataproc.v1.{Cluster, ClusterOperationMetadata}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class BaseFakeGoogleDataprocService extends GoogleDataprocService[IO] {
  override def createCluster(
    project: GoogleProject,
    region: RegionName,
    clusterName: DataprocClusterName,
    createClusterConfig: Option[CreateClusterConfig]
  )(implicit ev: ApplicativeAsk[IO, TraceId]): IO[CreateClusterResponse] = IO.pure(CreateClusterResponse.AlreadyExists)

  override def deleteCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
    implicit ev: ApplicativeAsk[IO, TraceId]
  ): IO[Option[ClusterOperationMetadata]] = IO.pure(none[ClusterOperationMetadata])

  override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
    implicit ev: ApplicativeAsk[IO, TraceId]
  ): IO[Option[Cluster]] = IO.pure(None)

  override def getClusterInstances(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
    implicit ev: ApplicativeAsk[IO, TraceId]
  ): IO[Map[DataprocRole, Set[InstanceName]]] = IO.pure(Map.empty)

  override def getClusterError(operationName: OperationName)(
    implicit ev: ApplicativeAsk[IO, TraceId]
  ): IO[Option[ClusterError]] = IO.pure(None)
}

object FakeGoogleDataprocService extends BaseFakeGoogleDataprocService
