package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.ApplicativeAsk
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1.{Cluster, CreateClusterRequest, Operation}
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId

class GoogleKubernetesInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
  clusterManagerClient: ClusterManagerClient,
  blocker: Blocker,
  blockerBound: Semaphore[F],
  retryConfig: RetryConfig
) extends GoogleKubernetesService[F] {

  override def createCluster(
    kubernetesClusterRequest: KubernetesCreateClusterRequest
  )(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
    val parent = Parent(kubernetesClusterRequest.project, kubernetesClusterRequest.location)

    val createClusterRequest: CreateClusterRequest = CreateClusterRequest
      .newBuilder()
      .setParent(parent.parentString)
      .setCluster(kubernetesClusterRequest.cluster)
      .build()

    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.createCluster(createClusterRequest)),
      f"com.google.cloud.container.v1.ClusterManagerClient.createCluster(${kubernetesClusterRequest})"
    )
  }

  override def getCluster(clusterId: KubernetesClusterId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Cluster] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.getCluster(clusterId.idString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.getCluster(${clusterId.idString})"
    )

  override def deleteCluster(clusterId: KubernetesClusterId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.deleteCluster(clusterId.idString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.deleteCluster(${clusterId.idString})"
    )

  private def tracedGoogleRetryWithBlocker[A](fa: F[A], action: String)(implicit ev: ApplicativeAsk[F, TraceId]): F[A] =
    blockerBound.withPermit(
      blocker.blockOn(
        tracedRetryGoogleF(retryConfig)(fa, action).compile.lastOrError
      )
    )
}
