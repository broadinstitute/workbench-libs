package org.broadinstitute.dsde.workbench.google2
package mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.container.v1.{Cluster, NodePool, NodePoolAutoscaling, Operation}
import org.broadinstitute.dsde.workbench.DoneCheckable
import org.broadinstitute.dsde.workbench.model.TraceId

import scala.concurrent.duration.FiniteDuration

class MockGKEService extends GKEService[IO] {
  override def createCluster(request: GKEModels.KubernetesCreateClusterRequest)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[com.google.api.services.container.model.Operation]] =
    IO(Some(new com.google.api.services.container.model.Operation().setName("opName")))

  override def deleteCluster(clusterId: GKEModels.KubernetesClusterId)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Operation]] = IO(Some(Operation.newBuilder().setName("opName").build()))

  val testEndpoint = "0.0.0.0"
  override def getCluster(clusterId: GKEModels.KubernetesClusterId)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Cluster]] = IO(Some(Cluster.newBuilder().setEndpoint(testEndpoint).build()))

  override def createNodepool(request: GKEModels.KubernetesCreateNodepoolRequest)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Operation]] =
    IO(Some(Operation.newBuilder().setName("opName").build()))

  override def getNodepool(nodepoolId: GKEModels.NodepoolId)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[NodePool]] = IO.pure(None)

  override def deleteNodepool(nodepoolId: GKEModels.NodepoolId)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Operation]] = IO(Some(Operation.newBuilder().setName("opName").build()))

  override def setNodepoolAutoscaling(nodepoolId: GKEModels.NodepoolId, autoscaling: NodePoolAutoscaling)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] = IO(Operation.newBuilder().setName("opName").build())

  override def setNodepoolSize(nodepoolId: GKEModels.NodepoolId, nodeCount: Int)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] = IO(Operation.newBuilder().setName("opName").build())

  override def pollOperation(operationId: GKEModels.KubernetesOperationId, delay: FiniteDuration, maxAttempts: Int)(
    implicit
    ev: Ask[IO, TraceId],
    doneEv: DoneCheckable[Operation]
  ): fs2.Stream[IO, Operation] =
    fs2.Stream(
      Operation
        .newBuilder()
        .setName("opName")
        .setStatus(Operation.Status.DONE)
        .build()
    )
}

object MockGKEService extends MockGKEService
