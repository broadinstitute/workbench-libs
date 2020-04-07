package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.ApplicativeAsk
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1.{Cluster, CreateClusterRequest, GetOperationRequest, IPAllocationPolicy, Operation}
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.{DoneCheckable, RetryConfig}
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId

import scala.concurrent.duration.FiniteDuration

class GKEInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
  clusterManagerClient: ClusterManagerClient,
  blocker: Blocker,
  blockerBound: Semaphore[F],
  retryConfig: RetryConfig
) extends GKEService[F] {

  override def createCluster(
    kubernetesClusterRequest: KubernetesCreateClusterRequest
  )(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
    val parent = Parent(kubernetesClusterRequest.project, kubernetesClusterRequest.location)

    println(s"network: ${kubernetesClusterRequest.cluster.getNetworkConfig.getNetwork}")

    val createClusterRequest: CreateClusterRequest = CreateClusterRequest
      .newBuilder()
      .setParent(parent.parentString)
      .setCluster(
        kubernetesClusterRequest.cluster
          .toBuilder
          .setIpAllocationPolicy( //it uses the legacy one, which is insecure, otherwise. See https://cloud.google.com/kubernetes-engine/docs/how-to/alias-ips
            IPAllocationPolicy
              .newBuilder()
              .setUseIpAliases(true)
          )
      )
      .build()

    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.createCluster(createClusterRequest)),
      f"com.google.cloud.container.v1.ClusterManagerClient.createCluster(${kubernetesClusterRequest})"
    )
  }

  override def getCluster(clusterId: KubernetesClusterId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Cluster]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        Async[F].delay(clusterManagerClient.getCluster(clusterId.idString)),
        whenStatusCode(404)
      ),
      f"com.google.cloud.container.v1.ClusterManagerClient.getCluster(${clusterId.idString})"
    )

  override def deleteCluster(clusterId: KubernetesClusterId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.deleteCluster(clusterId.idString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.deleteCluster(${clusterId.idString})"
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
