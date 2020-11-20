package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.Ask
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1.{
  Cluster,
  CreateNodePoolRequest,
  GetOperationRequest,
  NodePool,
  NodePoolAutoscaling,
  Operation,
  SetNodePoolAutoscalingRequest,
  SetNodePoolSizeRequest
}
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.{DoneCheckable, RetryConfig}
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId
import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration

final class GKEInterpreter[F[_]: StructuredLogger: Timer: ContextShift](
  clusterManagerClient: ClusterManagerClient,
  legacyClient: com.google.api.services.container.Container,
  blocker: Blocker,
  blockerBound: Semaphore[F],
  retryConfig: RetryConfig
)(implicit F: Async[F])
    extends GKEService[F] {

  override def createCluster(
    request: KubernetesCreateClusterRequest
  )(implicit ev: Ask[F, TraceId]): F[Option[com.google.api.services.container.model.Operation]] = {
    val parent = Parent(request.project, request.location).toString

    // Note createCluster uses the legacy com.google.api.services.container client rather than
    // the newer com.google.container.v1 client because certain options like Workload Identity
    // are only available in the old client.

    val googleRequest = new com.google.api.services.container.model.CreateClusterRequest()
      .setCluster(request.cluster)

    tracedGoogleRetryWithBlocker(
      recoverF(
        F.delay(legacyClient.projects().locations().clusters().create(parent, googleRequest).execute()),
        whenStatusCode(409)
      ),
      s"com.google.api.services.container.Projects.Locations.Cluster(${request})"
    )
  }

  override def getCluster(clusterId: KubernetesClusterId)(implicit ev: Ask[F, TraceId]): F[Option[Cluster]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        F.delay(clusterManagerClient.getCluster(clusterId.toString)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.container.v1.ClusterManagerClient.getCluster(${clusterId.toString})"
    )

  override def deleteCluster(
    clusterId: KubernetesClusterId
  )(implicit ev: Ask[F, TraceId]): F[Option[Operation]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        F.delay(clusterManagerClient.deleteCluster(clusterId.toString)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.container.v1.ClusterManagerClient.deleteCluster(${clusterId.toString})"
    )

  override def createNodepool(
    request: KubernetesCreateNodepoolRequest
  )(implicit ev: Ask[F, TraceId]): F[Option[Operation]] = {
    val createNodepoolRequest: CreateNodePoolRequest = CreateNodePoolRequest
      .newBuilder()
      .setParent(request.clusterId.toString)
      .setNodePool(request.nodepool.toBuilder)
      .build()

    tracedGoogleRetryWithBlocker(
      recoverF(
        F.delay(
          clusterManagerClient.createNodePool(createNodepoolRequest)
        ),
        whenStatusCode(409)
      ),
      s"com.google.api.services.container.Projects.Locations.Cluster.Nodepool(${request})"
    )
  }

  override def getNodepool(nodepoolId: NodepoolId)(implicit ev: Ask[F, TraceId]): F[Option[NodePool]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        F.delay(clusterManagerClient.getNodePool(nodepoolId.toString)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.container.v1.ClusterManagerClient.getNodepool(${nodepoolId.toString})"
    )

  override def deleteNodepool(nodepoolId: NodepoolId)(implicit ev: Ask[F, TraceId]): F[Option[Operation]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        F.delay(clusterManagerClient.deleteNodePool(nodepoolId.toString)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.container.v1.ClusterManagerClient.deleteNodepool(${nodepoolId.toString})"
    )

  override def setNodepoolAutoscaling(nodepoolId: NodepoolId, autoscaling: NodePoolAutoscaling)(
    implicit ev: Ask[F, TraceId]
  ): F[Operation] = {
    val request =
      SetNodePoolAutoscalingRequest.newBuilder().setName(nodepoolId.toString).setAutoscaling(autoscaling).build()

    tracedGoogleRetryWithBlocker(
      F.delay(clusterManagerClient.setNodePoolAutoscaling(request)),
      s"com.google.cloud.container.v1.ClusterManagerClient.setNodePoolAutoscaling(${nodepoolId.toString}, ${autoscaling.toString})"
    )
  }

  override def setNodepoolSize(nodepoolId: NodepoolId, nodeCount: Int)(implicit ev: Ask[F, TraceId]): F[Operation] = {
    val request = SetNodePoolSizeRequest.newBuilder().setName(nodepoolId.toString).setNodeCount(nodeCount).build()

    tracedGoogleRetryWithBlocker(
      F.delay(clusterManagerClient.setNodePoolSize(request)),
      s"com.google.cloud.container.v1.ClusterManagerClient.setNodePoolSize(${nodepoolId.toString}, ${nodeCount})"
    )
  }

  //delete and create operations take around ~5mins with simple tests, could be longer for larger clusters
  override def pollOperation(operationId: KubernetesOperationId, delay: FiniteDuration, maxAttempts: Int)(implicit
    ev: Ask[F, TraceId],
    doneEv: DoneCheckable[Operation]
  ): Stream[F, Operation] = {
    val request = GetOperationRequest
      .newBuilder()
      .setName(operationId.idString)
      .build()

    val getOperation = for {
      op <- F.delay(clusterManagerClient.getOperation(request))
      _ <-
        if (op.getStatusMessage.isEmpty) F.unit
        else F.raiseError[Unit](new RuntimeException("Operation failed due to: " + op.getStatusMessage))
    } yield op

    streamFUntilDone(getOperation, maxAttempts, delay)
  }

  private def tracedGoogleRetryWithBlocker[A](fa: F[A], action: String)(implicit ev: Ask[F, TraceId]): F[A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(
                                      blocker.blockOn(fa)
                                    ),
                                    action
    ).compile.lastOrError
}
