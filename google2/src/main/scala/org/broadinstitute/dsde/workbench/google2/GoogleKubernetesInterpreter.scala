package org.broadinstitute.dsde.workbench.google2

import java.io.FileReader

import cats.effect.{Async, ContextShift, Timer}
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1.{Cluster, CreateClusterRequest, NodePool, NodePoolAutoscaling, Operation}
import io.chrisdavenport.log4cats.StructuredLogger
import io.kubernetes.client.{ApiClient, Configuration}
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.util.{ClientBuilder, KubeConfig}
import org.broadinstitute.dsde.workbench.RetryConfig
import cats.implicits._

import sys.process._

class GoogleKubernetesInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
                                         clusterManagerClient: ClusterManagerClient,
//                                         blocker: Blocker,
//                                         blockerBound: Semaphore[F],
                                         retryConfig: RetryConfig
                                       ) extends GoogleKubernetesService[F] {


  //
   override def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest): F[KubernetesClusterIdentifier] = {
     //TODO: this portion is mainly to highlight possibly relevant API fields. This will require additional design consideration to use intelligently
     //It is possible we want to expose things other than the fields in the request ADT to clients, and control other things from this library directly
     val clusterConfig: Cluster = Cluster
       .newBuilder()
       .setName(kubernetesClusterRequest.clusterName.value) //required
       .addNodePools(getNodePoolBuilder(NodePoolConfig(GoogleKubernetesService.DEFAULT_NODEPOOL_SIZE, kubernetesClusterRequest.initialNodePoolName))) //required
       .build() //builds recursively

    val createClusterRequest: CreateClusterRequest = CreateClusterRequest
      .newBuilder()
      .setParent(kubernetesClusterRequest.parent.parentString)
      .setCluster(clusterConfig)
      .build()

     for {
       _ <- Async[F].delay(clusterManagerClient.createCluster(createClusterRequest))
       id = KubernetesClusterIdentifier(kubernetesClusterRequest.parent.project, kubernetesClusterRequest.parent.location, kubernetesClusterRequest.clusterName)
     } yield id
  }

  override def getCluster(clusterId: KubernetesClusterIdentifier): F[Cluster] =
    Async[F].delay(clusterManagerClient.getCluster(clusterId.idString))

  override def deleteCluster(clusterId: KubernetesClusterIdentifier): F[Operation] =
    Async[F].delay(clusterManagerClient.deleteCluster(clusterId.idString))

  override def initAuthToCluster(clusterId: KubernetesClusterIdentifier): F[Unit] =
    for {
     _ <- setupClusterCredentials(clusterId)
    } yield ()


  def testClientInteraction() = {
    val kubeConfigPath: String = "/Users/jcanas/.kube/config" //TODO: where will this be on leo?
    val client: ApiClient = ClientBuilder.kubeconfig(
      KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))
    ).build()

    Configuration.setDefaultApiClient(client)
    val api = new CoreV1Api

    val list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null)
    println(list.getItems)

    Async[F].delay(())
  }

  private def setupClusterCredentials(clusterId: KubernetesClusterIdentifier): F[Int] = {
    val getCredentialsCmd = s"gcloud container clusters get-credentials --project ${clusterId.project.value} --region ${clusterId.location.value} ${clusterId.clusterName.value}"

    Async[F].delay(getCredentialsCmd.!)
  }


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



}
