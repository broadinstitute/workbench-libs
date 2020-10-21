package org.broadinstitute.dsde.workbench
package google2

import java.nio.file.Paths
import java.util.UUID

import scala.collection.JavaConverters._
import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, IO}
import cats.mtl.ApplicativeAsk
import com.google.api.services.container.model.SandboxConfig
import com.google.container.v1._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesConstants._
import org.broadinstitute.dsde.workbench.google2.KubernetesModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.concurrent.duration._

//TODO: migrate to a unit test
//TODO: investigate running minikube in a docker for unit/automation tests https://banzaicloud.com/blog/minikube-ci/
final class Test(credPathStr: String,
                 projectStr: String = "broad-dsde-dev",
                 regionStr: String = "us-central1",
                 clusterNameStr: String = "test-cluster",
                 nodepoolNameStr: String = "test-nodepool",
                 defaultNamespaceNameStr: String = "test-namespace",
                 networkNameStr: String = "kube-test") {

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

  val secrets: Map[SecretKey, Array[Byte]] = Map(
    SecretKey("example-secret") -> "supersecretsecretsaresecretive".getBytes()
  )
  val kubeSecret =
    KubernetesSecret(defaultNamespaceName.right.get, SecretName("secret1"), secrets, KubernetesSecretType.Generic)

  def makeClusterId(name: String) = KubernetesClusterId(project, region, KubernetesClusterName(name))

  def callCreateCluster(
    clusterId: KubernetesClusterId = clusterId
  ): IO[Option[com.google.api.services.container.model.Operation]] = {
    val ips = List("69.173.127.0/25", "69.173.124.0/23")
    val network: String = KubernetesNetwork(project, NetworkName(networkNameStr)).idString

    val cluster =
      new com.google.api.services.container.model.Cluster()
        .setName(clusterName.right.get.value)
        .setNodePools(List(getLegacyNodepool(getDefaultNodepoolConfig(nodepoolName.right.get))).asJava)
        .setNetwork(network)
        .setSubnetwork(KubernetesSubNetwork(project, RegionName(regionStr), SubnetworkName(subnetworkNameStr)).idString)
        .setNetworkPolicy(getDefaultLegacyNetworkPolicy())
        .setMasterAuthorizedNetworksConfig(
          new com.google.api.services.container.model.MasterAuthorizedNetworksConfig()
            .setEnabled(true)
            .setCidrBlocks(
              ips.map(ip => new com.google.api.services.container.model.CidrBlock().setCidrBlock(ip)).asJava
            )
        )

    serviceResource.use { service =>
      service.createCluster(KubernetesCreateClusterRequest(project, region, cluster))
    }
  }

  def callDeleteCluster(clusterId: KubernetesClusterId = clusterId): IO[Option[Operation]] = serviceResource.use {
    service =>
      service.deleteCluster(KubernetesClusterId(project, region, clusterName.right.get))
  }

  def callGetCluster(clusterId: KubernetesClusterId = clusterId): IO[Option[Cluster]] = serviceResource.use { service =>
    service.getCluster(KubernetesClusterId(project, region, clusterName.right.get))
  }

  def callCreateNodepool(clusterId: KubernetesClusterId = clusterId,
                         nodepoolNameStr: String): IO[Option[com.google.api.services.container.model.Operation]] = {
    val nodepoolName = KubernetesName.withValidation[NodepoolName](nodepoolNameStr, NodepoolName.apply)
    val nodepoolConfig = getDefaultNodepoolConfig(nodepoolName.right.get)
    val nodepool = getNodepoolBuilder(nodepoolConfig).build()

    serviceResource.use { service =>
      service.createNodepool(KubernetesCreateNodepoolRequest(clusterId, nodepool))
    }
  }

  def callGetNodepool(nodepoolId: NodepoolId): IO[Option[NodePool]] = serviceResource.use { service =>
    service.getNodepool(nodepoolId)
  }

  def callDeleteNodepool(nodepoolId: NodepoolId): IO[Option[Operation]] = serviceResource.use { service =>
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

  def callDeleteNamespace(
    clusterId: KubernetesClusterId = clusterId,
    namespace: KubernetesNamespace = KubernetesNamespace(defaultNamespaceName.right.get)
  ): IO[Unit] =
    kubeService.use { k =>
      k.deleteNamespace(clusterId, namespace)
    }

  def callCreateServiceAccount(
    clusterId: KubernetesClusterId = clusterId,
    ksaNameStr: String = "test-service-account",
    ksaAnnotations: Map[String, String] = Map("ksa" -> "gsa", "foo" -> "bar"),
    namespace: KubernetesNamespace = KubernetesNamespace(defaultNamespaceName.right.get)
  ): IO[Unit] = {
    val ksaName = KubernetesName.withValidation[ServiceAccountName](ksaNameStr, ServiceAccountName.apply).right.get
    val ksa = KubernetesServiceAccount(ksaName, ksaAnnotations)
    kubeService.use { k =>
      k.createServiceAccount(clusterId, ksa, namespace)
    }
  }

  def callCreateRole(
    clusterId: KubernetesClusterId = clusterId,
    roleNameStr: String = "test-role",
    namespace: KubernetesNamespace = KubernetesNamespace(defaultNamespaceName.right.get)
  ): IO[Unit] = {
    val roleName = KubernetesName.withValidation[RoleName](roleNameStr, RoleName.apply).right.get
    val rules = getDefaultRules()
    val role = KubernetesRole(roleName, rules)

    kubeService.use { k =>
      k.createRole(clusterId, role, namespace)
    }
  }

  def callCreateSecret(clusterId: KubernetesClusterId = clusterId,
                       secret: KubernetesSecret = kubeSecret,
                       namespace: KubernetesNamespace = KubernetesNamespace(defaultNamespaceName.right.get)): IO[Unit] =
    kubeService.use { k =>
      k.createSecret(clusterId, namespace, secret)
    }

  def callCreateRoleBinding(
    clusterId: KubernetesClusterId = clusterId,
    roleBindingNameStr: String = "test-role-binding",
    roleNameStr: String = "test-role",
    ksaNameStr: String = "test-service-account",
    namespace: KubernetesNamespace = KubernetesNamespace(defaultNamespaceName.right.get)
  ): IO[Unit] = {
    val roleBindingName =
      KubernetesName.withValidation[RoleBindingName](roleBindingNameStr, RoleBindingName.apply).right.get
    val subjects = List(
      KubernetesSubject(KubernetesSubjectKind.ServiceAccount, SubjectKindName(ksaNameStr), namespace.name)
    )
    val roleRef =
      KubernetesRoleRef(ApiGroupName("rbac.authorization.k8s.io"), KubernetesRoleRefKind.Role, RoleName(roleNameStr))
    val roleBinding = KubernetesRoleBinding(roleBindingName, roleRef, subjects)

    kubeService.use { k =>
      k.createRoleBinding(clusterId, roleBinding, namespace)
    }
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
    import org.broadinstitute.dsde.workbench.DoneCheckableInstances._
    import org.broadinstitute.dsde.workbench.DoneCheckableSyntax._

    serviceResource.use { s =>
      for {
        lastOp <- s
          .pollOperation(KubernetesOperationId(project, region, operation.getName), 5 seconds, 72)
          .compile
          .lastOrError
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
  val DEFAULT_LOADBALANCER_PORTS = Set(
    ServicePort(PortNum(8080),
                KubernetesName.withValidation("testport", PortName).right.get,
                TargetPortNum(8080),
                Protocol("TCP"))
  )

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

  def getDefaultLegacyNetworkPolicy(): com.google.api.services.container.model.NetworkPolicy =
    new com.google.api.services.container.model.NetworkPolicy()
      .setEnabled(true)

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

  def getLegacyNodepool(config: NodepoolConfig): com.google.api.services.container.model.NodePool =
    new com.google.api.services.container.model.NodePool()
      .setConfig(
        new com.google.api.services.container.model.NodeConfig()
          .setMachineType(config.machineType.value)
          .setDiskSizeGb(config.diskSize)
          .setServiceAccount(config.serviceAccount.value)
      )
      .setInitialNodeCount(config.initialNodes)
      .setName(config.name.value)
      .setManagement(
        new com.google.api.services.container.model.NodeManagement().setAutoUpgrade(true).setAutoRepair(true)
      )
      .setAutoscaling(
        new com.google.api.services.container.model.NodePoolAutoscaling()
          .setEnabled(true)
          .setMinNodeCount(config.autoscalingConfig.minimumNodes)
          .setMaxNodeCount(config.autoscalingConfig.maximumNodes)
      )

  def getDefaultRules(): List[KubernetesPolicyRule] = {
    // Rule 1 allows all
    val apiGroups1 = Set(KubernetesApiGroup(ApiGroupName("*")))
    val resources1 = Set(KubernetesResource(ResourceName("*")))
    val verbs1 = Set(KubernetesVerb(VerbName("*")))
    val rule1 = KubernetesPolicyRule(apiGroups1, resources1, verbs1)

    // Rule 2 is more specific
    val apiGroups2 = Set(KubernetesApiGroup(ApiGroupName("extensions")), KubernetesApiGroup(ApiGroupName("app")))
    val resources2 = Set(KubernetesResource(ResourceName("jobs")), KubernetesResource(ResourceName("ingresses")))
    val verbs2 = Set(KubernetesVerb(VerbName("get")), KubernetesVerb(VerbName("update")))
    val rule2 = KubernetesPolicyRule(apiGroups2, resources2, verbs2)

    List(rule1, rule2)
  }
}
