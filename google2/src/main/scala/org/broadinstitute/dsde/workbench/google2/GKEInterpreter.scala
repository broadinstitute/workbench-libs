package org.broadinstitute.dsde.workbench.google2

import cats.Show
import cats.effect.Async
import cats.effect.std.Semaphore
import cats.mtl.Ask
import cats.syntax.all._
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1._
import fs2.Stream
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.withLogging
import org.broadinstitute.dsde.workbench.{DoneCheckable, RetryConfig}
import org.typelevel.log4cats.StructuredLogger

import scala.concurrent.duration.FiniteDuration

final class GKEInterpreter[F[_]: StructuredLogger](
  clusterManagerClient: ClusterManagerClient,
  legacyClient: com.google.api.services.container.Container,
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
        F.blocking(legacyClient.projects().locations().clusters().create(parent, googleRequest).execute()),
        whenStatusCode(409)
      ),
      s"com.google.api.services.container.Projects.Locations.Cluster($request)"
    )
  }

  override def getCluster(clusterId: KubernetesClusterId)(implicit ev: Ask[F, TraceId]): F[Option[Cluster]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        F.blocking(clusterManagerClient.getCluster(clusterId.toString)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.container.v1.ClusterManagerClient.getCluster(${clusterId.toString})",
      Show.show[Option[Cluster]](c => c.fold("null")(cc => s"status: ${cc.getStatus}"))
    )

  override def deleteCluster(
    clusterId: KubernetesClusterId
  )(implicit ev: Ask[F, TraceId]): F[Option[Operation]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        F.blocking(clusterManagerClient.deleteCluster(clusterId.toString)),
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
        F.blocking(
          clusterManagerClient.createNodePool(createNodepoolRequest)
        ),
        whenStatusCode(409)
      ),
      s"com.google.api.services.container.Projects.Locations.Cluster.Nodepool($request)"
    )
  }

  override def getNodepool(nodepoolId: NodepoolId)(implicit ev: Ask[F, TraceId]): F[Option[NodePool]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        F.blocking(clusterManagerClient.getNodePool(nodepoolId.toString)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.container.v1.ClusterManagerClient.getNodepool(${nodepoolId.toString})"
    )

  override def deleteNodepool(nodepoolId: NodepoolId)(implicit ev: Ask[F, TraceId]): F[Option[Operation]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        F.blocking(clusterManagerClient.deleteNodePool(nodepoolId.toString)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.container.v1.ClusterManagerClient.deleteNodepool(${nodepoolId.toString})"
    )

  override def setNodepoolAutoscaling(nodepoolId: NodepoolId, autoscaling: NodePoolAutoscaling)(implicit
    ev: Ask[F, TraceId]
  ): F[Operation] = {
    val request =
      SetNodePoolAutoscalingRequest.newBuilder().setName(nodepoolId.toString).setAutoscaling(autoscaling).build()

    tracedGoogleRetryWithBlocker(
      F.blocking(clusterManagerClient.setNodePoolAutoscaling(request)),
      s"com.google.cloud.container.v1.ClusterManagerClient.setNodePoolAutoscaling(${nodepoolId.toString}, ${autoscaling.toString})"
    )
  }

  override def setNodepoolSize(nodepoolId: NodepoolId, nodeCount: Int)(implicit ev: Ask[F, TraceId]): F[Operation] = {
    val request = SetNodePoolSizeRequest.newBuilder().setName(nodepoolId.toString).setNodeCount(nodeCount).build()

    tracedGoogleRetryWithBlocker(
      F.blocking(clusterManagerClient.setNodePoolSize(request)),
      s"com.google.cloud.container.v1.ClusterManagerClient.setNodePoolSize(${nodepoolId.toString}, $nodeCount)"
    )
  }

  // delete and create operations take around ~5mins with simple tests, could be longer for larger clusters
  override def pollOperation(operationId: KubernetesOperationId, delay: FiniteDuration, maxAttempts: Int)(implicit
    ev: Ask[F, TraceId],
    doneEv: DoneCheckable[Operation]
  ): Stream[F, Operation] = {
    val request = GetOperationRequest
      .newBuilder()
      .setName(operationId.idString)
      .build()

    val getOperation = for {
      traceId <- ev.ask
      op <- withLogging(
        F.blocking(clusterManagerClient.getOperation(request)),
        Some(traceId),
        s"com.google.cloud.container.v1.ClusterManagerClient.getOperation($operationId)",
        Show[Operation](op =>
          if (op == null)
            "null"
          else
            s"operationType=${op.getOperationType}, progress=${op.getProgress}, status=${op.getStatus}, startTime=${op.getStartTime}, statusMessage=${op.getStatusMessage}"
        )
      )
      _ <-
        if (op.getStatusMessage.isEmpty) F.unit
        else F.raiseError[Unit](new RuntimeException("Operation failed due to: " + op.getStatusMessage))
    } yield op

    streamFUntilDone(getOperation, maxAttempts, delay)
  }

  private def tracedGoogleRetryWithBlocker[A](fa: F[A],
                                              action: String,
                                              resultFormatter: Show[A] =
                                                Show.show[A](a => if (a == null) "null" else a.toString.take(1024))
  )(implicit ev: Ask[F, TraceId]): F[A] =
    tracedRetryF(retryConfig)(blockerBound.permit.use(_ => fa), action, resultFormatter).compile.lastOrError
}
