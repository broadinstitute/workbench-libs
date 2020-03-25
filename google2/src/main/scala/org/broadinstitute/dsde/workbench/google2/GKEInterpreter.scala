package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.ApplicativeAsk
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1.{Cluster, CreateClusterRequest, Operation}
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId

class GKEInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
  clusterManagerClient: ClusterManagerClient,
  blocker: Blocker,
  blockerBound: Semaphore[F],
  retryConfig: RetryConfig
)(implicit ev: ApplicativeAsk[F, TraceId])
    extends GKEService[F] {

  override def createCluster(
    kubernetesClusterRequest: KubernetesCreateClusterRequest
  ): F[Operation] = {
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

  override def getCluster(clusterId: KubernetesClusterId): F[Option[Cluster]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        Async[F].delay(clusterManagerClient.getCluster(clusterId.idString)),
        whenStatusCode(404)
      ),
      f"com.google.cloud.container.v1.ClusterManagerClient.getCluster(${clusterId.idString})"
    )

  override def deleteCluster(clusterId: KubernetesClusterId): F[Operation] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.deleteCluster(clusterId.idString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.deleteCluster(${clusterId.idString})"
    )

  private def tracedGoogleRetryWithBlocker[A](fa: F[A], action: String): F[A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(
                                      blocker.blockOn(fa)
                                    ),
                                    action).compile.lastOrError

}
