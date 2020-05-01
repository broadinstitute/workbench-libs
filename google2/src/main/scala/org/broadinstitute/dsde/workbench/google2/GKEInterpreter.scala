package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.ApplicativeAsk
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1.{
  Cluster,
  CreateClusterRequest,
  CreateNodePoolRequest,
  GetOperationRequest,
  IPAllocationPolicy,
  NodePool,
  Operation
}
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.{DoneCheckable, RetryConfig}
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId

import scala.concurrent.duration.FiniteDuration

final class GKEInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
  clusterManagerClient: ClusterManagerClient,
  blocker: Blocker,
  blockerBound: Semaphore[F],
  retryConfig: RetryConfig
) extends GKEService[F] {

  override def createCluster(
    request: KubernetesCreateClusterRequest
  )(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
    val parent = Parent(request.project, request.location)

    val createClusterRequest: CreateClusterRequest = CreateClusterRequest
      .newBuilder()
      .setParent(parent.toString)
      .setCluster(
        request.cluster.toBuilder
          .setIpAllocationPolicy( //otherwise it uses the legacy one, which is insecure. See https://cloud.google.com/kubernetes-engine/docs/how-to/alias-ips
            IPAllocationPolicy
              .newBuilder()
              .setUseIpAliases(true)
          )
      )
      .build()

    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.createCluster(createClusterRequest)),
      f"com.google.cloud.container.v1.ClusterManagerClient.createCluster(${request})"
    )
  }

  override def getCluster(clusterId: KubernetesClusterId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Cluster]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        Async[F].delay(clusterManagerClient.getCluster(clusterId.toString)),
        whenStatusCode(404)
      ),
      f"com.google.cloud.container.v1.ClusterManagerClient.getCluster(${clusterId.toString})"
    )

  override def deleteCluster(clusterId: KubernetesClusterId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.deleteCluster(clusterId.toString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.deleteCluster(${clusterId.toString})"
    )

  override def createNodepool(
    request: KubernetesCreateNodepoolRequest
  )(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
    val createNodepoolRequest: CreateNodePoolRequest = CreateNodePoolRequest
      .newBuilder()
      .setParent(request.clusterId.toString)
      .setNodePool(request.nodepool.toBuilder)
      .build()

    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.createNodePool(createNodepoolRequest)),
      f"com.google.cloud.container.v1.ClusterManagerClient.createNodepool(${request})"
    )
  }

  override def getNodepool(nodepoolId: NodepoolId)(implicit ev: ApplicativeAsk[F, TraceId]): F[NodePool] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.getNodePool(nodepoolId.toString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.getNodepool(${nodepoolId.toString})"
    )

  override def deleteNodepool(nodepoolId: NodepoolId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.deleteNodePool(nodepoolId.toString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.deleteNodepool(${nodepoolId.toString})"
    )

  //delete and create operations take around ~5mins with simple tests, could be longer for larger clusters
  override def pollOperation(operationId: KubernetesOperationId, delay: FiniteDuration, maxAttempts: Int)(
    implicit ev: ApplicativeAsk[F, TraceId],
    doneEv: DoneCheckable[Operation]
  ): Stream[F, Operation] = {
    val request = GetOperationRequest
      .newBuilder()
      .setName(operationId.idString)
      .build()

    val getOperation = Async[F].delay(clusterManagerClient.getOperation(request))
    streamFUntilDone(getOperation, maxAttempts, delay)
  }

  private def tracedGoogleRetryWithBlocker[A](fa: F[A], action: String)(implicit ev: ApplicativeAsk[F, TraceId]): F[A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(
                                      blocker.blockOn(fa)
                                    ),
                                    action).compile.lastOrError

}
