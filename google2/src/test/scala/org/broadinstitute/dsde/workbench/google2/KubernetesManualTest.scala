package org.broadinstitute.dsde.workbench
package google2

import java.nio.file.Paths
import java.util.UUID

import scala.concurrent.duration._
import cats.effect.{Blocker, IO}
import cats.effect.concurrent.Semaphore
import cats.mtl.ApplicativeAsk
import com.google.container.v1.{
  Cluster,
  NetworkPolicy,
  NodeConfig,
  NodeManagement,
  NodePool,
  NodePoolAutoscaling,
  Operation
}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GoogleProject, ServiceAccountName}
import KubernetesConstants._

//TODO: migrate to a unit test
//TODO: investigate running minikube in a docker for unit/automation tests https://banzaicloud.com/blog/minikube-ci/
final class Test(credPathStr: String,
                 projectStr: String = "broad-dsde-dev",
                 regionStr: String = "us-central1",
                 clusterNameStr: String = "test-cluster",
                 nodepoolNameStr: String = "test-nodepool",
                 defaultNamespaceNameStr: String = "test-namespace",
                 networkNameStr: String = "test-network") {

  import scala.concurrent.ExecutionContext.global

  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit val traceId = ApplicativeAsk.const[IO, TraceId](TraceId(UUID.randomUUID()))

  implicit def logger = Slf4jLogger.getLogger[IO]

  val blocker = Blocker.liftExecutionContext(global)
  val semaphore = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject(projectStr)
  val region = Location(regionStr)
  val zoneStr = s"${region}-a"
  val subnetworkNameStr = networkNameStr
  val clusterName = KubernetesName.withValidation[KubernetesClusterName](clusterNameStr, KubernetesClusterName.apply)
  val nodepoolName = KubernetesName.withValidation[NodepoolName](nodepoolNameStr, NodepoolName.apply)

  val defaultNamespaceName =
    KubernetesName.withValidation[NamespaceName](defaultNamespaceNameStr, NamespaceName.apply)

  val clusterId = KubernetesClusterId(project, region, clusterName.right.get)

  val credPath = Paths.get(credPathStr)
  val serviceResource = GKEService.resource(credPath, blocker, semaphore)

  def makeClusterId(name: String) = KubernetesClusterId(project, region, KubernetesClusterName(name))

  def callCreateCluster(clusterId: KubernetesClusterId = clusterId): IO[Operation] = {
    val network: String = KubernetesNetwork(project, NetworkName(networkNameStr)).idString
    val cluster = getDefaultCluster(nodepoolName.right.get, clusterName.right.get).toBuilder
      .setNetwork(network) // needs to be a VPC network
      .setSubnetwork(KubernetesSubNetwork(project, RegionName(regionStr), SubnetworkName(subnetworkNameStr)).idString)
      .setNetworkPolicy(getDefaultNetworkPolicy()) // needed for security
      .build()

    serviceResource.use { service =>
      service.createCluster(KubernetesCreateClusterRequest(project, region, cluster))
    }
  }

  def callDeleteCluster(clusterId: KubernetesClusterId = clusterId): IO[Operation] = serviceResource.use { service =>
    service.deleteCluster(KubernetesClusterId(project, region, clusterName.right.get))
  }

  def callGetCluster(clusterId: KubernetesClusterId = clusterId): IO[Option[Cluster]] = serviceResource.use { service =>
    service.getCluster(KubernetesClusterId(project, region, clusterName.right.get))
  }

  def callCreateNodepool(clusterId: KubernetesClusterId = clusterId, nodepoolNameStr: String): IO[Operation] = {
    val nodepoolName = KubernetesName.withValidation[NodepoolName](nodepoolNameStr, NodepoolName.apply)
    val nodepoolConfig = getDefaultNodepoolConfig(nodepoolName.right.get)
    val nodepool = getNodepoolBuilder(nodepoolConfig).build()

    serviceResource.use { service =>
      service.createNodepool(KubernetesCreateNodepoolRequest(clusterId, nodepool))
    }
  }

  def callGetNodepool(nodepoolId: NodepoolId): IO[NodePool] = serviceResource.use { service =>
    service.getNodepool(nodepoolId)
  }

  def callDeleteNodepool(nodepoolId: NodepoolId): IO[Operation] = serviceResource.use { service =>
    service.deleteNodepool(nodepoolId)
  }

  val kubeService = for {
    gs <- GKEService.resource(credPath, blocker, semaphore)
    ks <- KubernetesService.resource(credPath, gs, blocker, semaphore)
  } yield ks

  def callCreateNamespace(
    clusterId: KubernetesClusterId = clusterId,
    namespace: KubernetesNamespace = KubernetesNamespace(defaultNamespaceName.right.get)
  ): IO[Unit] =
    kubeService.use { k =>
      k.createNamespace(clusterId, namespace)
    }

  def testGetClient(clusterId: KubernetesClusterId = clusterId): IO[Unit] =
    kubeService.use { k =>
      for {
        _ <- k.createNamespace(clusterId, KubernetesNamespace(NamespaceName("diff1")))
        _ <- k.createNamespace(clusterId, KubernetesNamespace(NamespaceName("diff2")))
      } yield ()
    }

  val DEFAULT_SERVICE_SELECTOR = KubernetesSelector(Map("user" -> "test-user"))

  val containers = Set(
    KubernetesContainer(ContainerName("container1"), Image("gcr.io/google-samples/node-hello:1.0"), None)
  )
  val pod = KubernetesPod(PodName("pod1"), containers, DEFAULT_SERVICE_SELECTOR)

  def callCreateService(clusterId: KubernetesClusterId = clusterId): IO[Unit] =
    kubeService.use { k =>
      k.createService(
        clusterId,
        KubernetesServiceKind.KubernetesLoadBalancerService(
          DEFAULT_SERVICE_SELECTOR,
          KubernetesConstants.DEFAULT_LOADBALANCER_PORTS,
          ServiceName("s3")
        ),
        KubernetesNamespace(defaultNamespaceName.right.get)
      )
    }

  def callCreatePod(clusterId: KubernetesClusterId = clusterId): IO[Unit] =
    kubeService.use { k =>
      k.createPod(clusterId, pod, KubernetesNamespace(defaultNamespaceName.right.get))
    }

  def testPolling(operation: Operation): IO[Unit] = {
    import org.broadinstitute.dsde.workbench.DoneCheckableSyntax._
    import org.broadinstitute.dsde.workbench.DoneCheckableInstances._

    serviceResource.use { s =>
      for {
        lastOp <- s.pollOperation(KubernetesOperationId(project, region, operation), 5 seconds, 72).compile.lastOrError
        _ <- if (lastOp.isDone) IO(println(s"operation is done, initial operation: ${operation}"))
        else IO(s"operation errored, initial operation: ${operation}")
      } yield ()
    }
  }
}

object KubernetesConstants {
  //this default namespace is initialized automatically with a kubernetes environment, and we do not create it
  val DEFAULT_NAMESPACE = "default"

  //composite of NodePort and ClusterIP types. Allows external access
  val DEFAULT_LOADBALANCER_PORTS = Set(ServicePort(8080))

  val DEFAULT_NODEPOOL_SIZE = 1
  val DEFAULT_NODEPOOL_MACHINE_TYPE = MachineTypeName("n1-standard-1")
  val DEFAULT_NODEPOOL_DISK_SIZE = 100 // GB
  val DEFAULT_NODEPOOL_SERVICE_ACCOUNT = ServiceAccountName("default")
  val DEFAULT_NODEPOOL_AUTOSCALING: NodepoolAutoscalingConfig = NodepoolAutoscalingConfig(1, 10)

  def getDefaultNodepoolConfig(nodepoolName: NodepoolName) = NodepoolConfig(
    DEFAULT_NODEPOOL_SIZE,
    nodepoolName,
    DEFAULT_NODEPOOL_MACHINE_TYPE,
    DEFAULT_NODEPOOL_DISK_SIZE,
    DEFAULT_NODEPOOL_SERVICE_ACCOUNT,
    DEFAULT_NODEPOOL_AUTOSCALING
  )

  def getDefaultNetworkPolicy(): NetworkPolicy =
    NetworkPolicy
      .newBuilder()
      .setEnabled(true)
      .build()

  def getDefaultCluster(nodepoolName: NodepoolName, clusterName: KubernetesClusterName): Cluster =
    Cluster
      .newBuilder()
      .setName(clusterName.value) //required
      .addNodePools(
        getNodepoolBuilder(getDefaultNodepoolConfig(nodepoolName))
      ) //required
      .build() //builds recursively

  def getNodepoolBuilder(config: NodepoolConfig): NodePool.Builder =
    NodePool
      .newBuilder()
      .setConfig(
        NodeConfig
          .newBuilder()
          .setMachineType(config.machineType.value)
          .setDiskSizeGb(config.diskSize)
          .setServiceAccount(config.serviceAccount.value)
      )
      .setInitialNodeCount(config.initialNodes)
      .setName(config.name.value)
      .setManagement(
        NodeManagement
          .newBuilder()
          .setAutoUpgrade(true)
          .setAutoRepair(true)
      )
      .setAutoscaling(
        NodePoolAutoscaling
          .newBuilder()
          .setEnabled(true)
          .setMinNodeCount(config.autoscalingConfig.minimumNodes)
          .setMaxNodeCount(config.autoscalingConfig.maximumNodes)
      )
}
