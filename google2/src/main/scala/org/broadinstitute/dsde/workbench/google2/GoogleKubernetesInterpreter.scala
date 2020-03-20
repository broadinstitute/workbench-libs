package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.ApplicativeAsk
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1.{Cluster, CreateClusterRequest, NodePool, NodePoolAutoscaling, Operation}
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId

class GoogleKubernetesInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
                                         clusterManagerClient: ClusterManagerClient,
                                         blocker: Blocker,
                                         blockerBound: Semaphore[F],
                                         retryConfig: RetryConfig
                                       ) extends GoogleKubernetesService[F] {

   override def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest)
                             (implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
     //TODO: this portion is mainly to highlight possibly relevant API fields. This will require additional design consideration to use intelligently
     val clusterConfig: Cluster = Cluster
       .newBuilder()
       .setName(kubernetesClusterRequest.clusterId.clusterName.value) //required
       .addNodePools(getNodePoolBuilder(NodePoolConfig(KubernetesConstants.DEFAULT_NODEPOOL_SIZE, kubernetesClusterRequest.initialNodePoolName))) //required
       .build() //builds recursively

     val parent = Parent(kubernetesClusterRequest.clusterId.project, kubernetesClusterRequest.clusterId.location)

    val createClusterRequest: CreateClusterRequest = CreateClusterRequest
      .newBuilder()
      .setParent(parent.parentString)
      .setCluster(clusterConfig)
      .build()

     tracedGoogleRetryWithBlocker(
       Async[F].delay(clusterManagerClient.createCluster(createClusterRequest)),
       f"com.google.cloud.container.v1.ClusterManagerClient.createCluster(${kubernetesClusterRequest})"
     )
  }

  override def getCluster(clusterId: KubernetesClusterId)
                         (implicit ev: ApplicativeAsk[F, TraceId]): F[Cluster] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.getCluster(clusterId.idString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.getCluster(${clusterId.idString})"
    )

  override def deleteCluster(clusterId: KubernetesClusterId)
                            (implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.deleteCluster(clusterId.idString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.deleteCluster(${clusterId.idString})"
    )

  private def getNodePoolBuilder(config: NodePoolConfig) = {
    NodePool
      .newBuilder()
      .setInitialNodeCount(config.initialNodes)
      .setName(config.name.value)
      .setAutoscaling(
        NodePoolAutoscaling
          .newBuilder()
          .setEnabled(true)
          .setMinNodeCount(config.autoscalingConfig.minimumNodes)
          .setMaxNodeCount(config.autoscalingConfig.maximumNodes)
      )
  }

  private def tracedGoogleRetryWithBlocker[A](fa: F[A], action: String)
                                             (implicit ev: ApplicativeAsk[F, TraceId]): F[A] = {
      blockerBound.withPermit(blocker.blockOn(
        tracedRetryGoogleF(retryConfig)(fa, action).compile.lastOrError
      ))
  }
}
