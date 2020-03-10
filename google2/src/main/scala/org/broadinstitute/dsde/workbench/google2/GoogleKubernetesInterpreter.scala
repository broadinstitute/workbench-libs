package org.broadinstitute.dsde.workbench.google2

import java.io.FileReader

import cats.effect.{Async, ContextShift, Resource, Timer}
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1.{AuthenticatorGroupsConfig, Cluster, CreateClusterRequest, NodePool, NodePoolAutoscaling, Operation}
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
   override def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest): F[Operation] = {
     //TODO: this portion is mainly to highlight possibly relevant API fields. This will require additional design consideration to use intelligently
     //It is possible we want to expose things other than the fields in the request ADT to clients, and control other things from this library directly
     val clusterConfig: Cluster = Cluster
       .newBuilder()
       .setName(kubernetesClusterRequest.clusterId.clusterName.value) //required
       .addNodePools(getNodePoolBuilder(NodePoolConfig(GoogleKubernetesService.DEFAULT_NODEPOOL_SIZE, kubernetesClusterRequest.initialNodePoolName))) //required
       .setAuthenticatorGroupsConfig(
         AuthenticatorGroupsConfig.newBuilder()
       )
       .build() //builds recursively

     val parent = Parent(kubernetesClusterRequest.clusterId.project, kubernetesClusterRequest.clusterId.location)

    val createClusterRequest: CreateClusterRequest = CreateClusterRequest
      .newBuilder()
      .setParent(parent.parentString)
      .setCluster(clusterConfig)
      .build()

     Async[F].delay(clusterManagerClient.createCluster(createClusterRequest))
  }

  override def getCluster(clusterId: KubernetesClusterIdentifier): F[Cluster] =
    Async[F].delay(clusterManagerClient.getCluster(clusterId.idString))

  override def deleteCluster(clusterId: KubernetesClusterIdentifier): F[Operation] =
    Async[F].delay(clusterManagerClient.deleteCluster(clusterId.idString))

  //TODO: deprecate
  override def initAuthToCluster(clusterId: KubernetesClusterIdentifier): F[Unit] =
     setupClusterCredentials(clusterId)

  def initKubeClient(clusterIds: Set[KubernetesClusterIdentifier], pathToKubeConfig: String, pathToGoogleServiceAccountCredential: String) : Resource[F, CoreV1Api] = {
    for {
      _ <- Resource.liftF(Async[F].delay(clusterIds.foreach(id => setupClusterCredentials(id))))
      api <- makeKubeClient(pathToKubeConfig)
    } yield api
  }

  private def makeKubeClient(pathToKubeConfig: String): Resource[F, CoreV1Api] = {
    for {
      fileReader <- Resource.make(
        Async[F].delay(new FileReader(pathToKubeConfig))
      )(reader => Async[F].delay(reader.close()))
      apiClient <- Resource.liftF(Async[F].delay(ClientBuilder.kubeconfig(
        KubeConfig.loadKubeConfig(fileReader)
      ).build()))
      _ = Configuration.setDefaultApiClient(apiClient)
    } yield new CoreV1Api
  }

  def testClientInteraction() = {
    val kubeConfigPath: String = "/Users/jcanas/.kube/config" //TODO: where will this be on leo?
    val reader = new FileReader(kubeConfigPath) //TODO: close this
    println("reader printing")
    println(reader)
    val client: ApiClient = ClientBuilder.kubeconfig(
      KubeConfig.loadKubeConfig(reader)
    ).build()

    Configuration.setDefaultApiClient(client)
    val api = new CoreV1Api

    val list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null)
    Async[F].delay(println("i did it" + list.getItems))
  }

  private def setupClusterCredentials(clusterId: KubernetesClusterIdentifier): F[Unit] = {
    val getCredentialsCmd = s"gcloud container clusters get-credentials --project ${clusterId.project.value} --region ${clusterId.location.value} ${clusterId.clusterName.value}"
    val getKubeSecretsCommand = "kubectl get secrets"

    for {
      _ <- Async[F].delay(getCredentialsCmd.!)
      _ <- Async[F].delay(getKubeSecretsCommand.!)
    } yield ()

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
