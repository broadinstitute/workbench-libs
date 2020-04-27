package org.broadinstitute.dsde.workbench.google2
import com.google.container.v1.{Cluster, NetworkPolicy, NodeManagement, NodePool, NodePoolAutoscaling, Operation}

import collection.JavaConverters._
import io.kubernetes.client.models.{
  V1Container,
  V1ContainerPort,
  V1Namespace,
  V1ObjectMeta,
  V1ObjectMetaBuilder,
  V1Pod,
  V1PodSpec,
  V1Service,
  V1ServicePort,
  V1ServiceSpec
}
import org.apache.commons.codec.binary.Base64
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesModels.ServicePort
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName._
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import cats.implicits._

object KubernetesConstants {
  //this default namespace is initialized automatically with a kubernetes environment, and we do not create it
  val DEFAULT_NAMESPACE = "default"
  val DEFAULT_POD_KIND = "Pod"
  val SERVICE_KIND = "Service"
  //for session affinity, see https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#service-v1-core
  val STICKY_SESSION_AFFINITY = "ClientIP"

  //composite of NodePort and ClusterIP types. Allows external access
  val DEFAULT_LOADBALANCER_PORTS = Set(ServicePort(8080))

  val DEFAULT_NODEPOOL_SIZE: Int = 1
  val DEFAULT_NODEPOOL_AUTOSCALING: ClusterNodePoolAutoscalingConfig = ClusterNodePoolAutoscalingConfig(1, 10)

  def getDefaultNetworkPolicy(): NetworkPolicy =
    NetworkPolicy
      .newBuilder()
      .setEnabled(true)
      .build()

  def getDefaultCluster(nodePoolName: NodePoolName, clusterName: KubernetesClusterName): Cluster =
    Cluster
      .newBuilder()
      .setName(clusterName.value) //required
      .addNodePools(
        getNodePoolBuilder(NodePoolConfig(DEFAULT_NODEPOOL_SIZE, nodePoolName, DEFAULT_NODEPOOL_AUTOSCALING))
      ) //required
      .build() //builds recursively

  def getNodePoolBuilder(config: NodePoolConfig) =
    NodePool
      .newBuilder()
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

// Common kubernetes models //
final case class KubernetesClusterNotFoundException(message: String) extends WorkbenchException
final case class KubernetesInvalidNameException(message: String) extends WorkbenchException

object KubernetesName {
  def withValidation[A](str: String, apply: String => A): Either[Throwable, A] = {
    val regex = "(?:[a-z](?:[-a-z0-9]{0,38}[a-z0-9])?)".r //this is taken directly from the google error message if you have an invalid nodepool name. Its not in the docs anywhere
    val isValidName: Boolean = regex.pattern.matcher(str).matches()

    if (isValidName) {
      Right(apply(str))
    } else {
      Left(
        KubernetesInvalidNameException(
          s"Kubernetes names must adhere to the following regex: ${regex}, you passed: ${str}."
        )
      )
    }
  }
}

// Google kubernetes client models //
object GKEModels {

  //"us-central1" is an example of a valid location.
  final case class Parent(project: GoogleProject, location: Location) {
    val parentString: String = s"projects/${project.value}/locations/${location.value}"
  }

  //the cluster must have a name, and a com.google.container.v1.NodePool. The NodePool must have an initialNodeCount and a name.
  //the cluster must also have a network and subnetwork. See KubernetesManual test for how to specify these.
  //Location can either contain a zone or not, ex: "us-central1" or "us-central1-a". The former will create the nodepool you specify in multiple zones, the latter a single nodepool
  //see getDefaultCluster for an example of construction with the minimum fields necessary, plus some others you almost certainly want to configure
  final case class KubernetesCreateClusterRequest(project: GoogleProject,
                                                  location: Location,
                                                  cluster: com.google.container.v1.Cluster)

  //this is NOT analogous to clusterName in the context of dataproc/GCE. A single cluster can have multiple nodes, pods, services, containers, deployments, etc.
  //clusters should most likely NOT be provisioned per user as they are today. More design/security research is needed
  final case class KubernetesClusterName(value: String)

  final case class ClusterNodePoolAutoscalingConfig(minimumNodes: Int, maximumNodes: Int)

  final case class NodePoolName(value: String)

  final case class NodePoolConfig(initialNodes: Int,
                                  name: NodePoolName,
                                  autoscalingConfig: ClusterNodePoolAutoscalingConfig =
                                    KubernetesConstants.DEFAULT_NODEPOOL_AUTOSCALING)

  final case class KubernetesClusterId(project: GoogleProject, location: Location, clusterName: KubernetesClusterName) {
    val idString: String = s"projects/${project.value}/locations/${location.value}/clusters/${clusterName.value}"
  }

  final case class KubernetesOperationId(project: GoogleProject, location: Location, operation: Operation) {
    val idString: String = s"projects/${project.value}/locations/${location.value}/operations/${operation.getName}"
  }

  final case class KubernetesNetwork(project: GoogleProject, name: NetworkName) {
    val idString: String = s"projects/${project.value}/global/networks/${name.value}"
  }

  final case class KubernetesSubNetwork(project: GoogleProject, region: RegionName, name: SubnetworkName) {
    val idString: String = s"projects/${project.value}/regions/${region.value}/subnetworks/${name.value}"
  }

}

// Kubernetes client models and traits //

//the V1ObjectMeta is generalized to provide both 'name' and 'labels', as well as other fields, for all kubernetes entities
sealed trait KubernetesSerializableName {
  def value: String
}

object KubernetesSerializableName {
  //this nesting of NamespaceName is necessary to prevent duplicating the code achieved by KubernetesSerializableName and KubernetesSerializable
  //namespaces also have criteria other than their name
  final case class KubernetesNamespaceName(value: String) extends KubernetesSerializableName
  final case class KubernetesServiceName(value: String) extends KubernetesSerializableName
  final case class KubernetesContainerName(value: String) extends KubernetesSerializableName
  final case class KubernetesPodName(value: String) extends KubernetesSerializableName
}

trait JavaSerializable[A, B] {
  def getJavaSerialization(a: A): B
}

object JavaSerializableInstances {

  import KubernetesModels._
  import JavaSerializableSyntax._

  private def getNameSerialization(name: KubernetesSerializableName): V1ObjectMeta = {
    val metadata = new V1ObjectMetaBuilder()
      .withNewName(name.value)
      .build()

    metadata
  }

  implicit val kubernetesNamespaceNameSerializable = new JavaSerializable[KubernetesNamespaceName, V1ObjectMeta] {
    def getJavaSerialization(name: KubernetesNamespaceName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesPodNameSerializable = new JavaSerializable[KubernetesPodName, V1ObjectMeta] {
    def getJavaSerialization(name: KubernetesPodName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesContainerNameSerializable = new JavaSerializable[KubernetesContainerName, V1ObjectMeta] {
    def getJavaSerialization(name: KubernetesContainerName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesServiceNameSerializable = new JavaSerializable[KubernetesServiceName, V1ObjectMeta] {
    def getJavaSerialization(name: KubernetesServiceName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesNamespaceSerializable = new JavaSerializable[KubernetesNamespace, V1Namespace] {
    def getJavaSerialization(kubernetesName: KubernetesNamespace): V1Namespace = {
      val v1Namespace = new V1Namespace()
      v1Namespace.metadata(kubernetesName.name.getJavaSerialization)
      v1Namespace
    }
  }

  implicit val containerPortSerializable = new JavaSerializable[ContainerPort, V1ContainerPort] {
    def getJavaSerialization(containerPort: ContainerPort): V1ContainerPort = {
      val v1Port = new V1ContainerPort()
      v1Port.containerPort(containerPort.value)
    }
  }

  implicit val kubernetesContainerSerializable = new JavaSerializable[KubernetesContainer, V1Container] {
    def getJavaSerialization(container: KubernetesContainer): V1Container = {
      val v1Container = new V1Container()
      v1Container.setName(container.name.value)
      v1Container.setImage(container.image.uri)

      // example on leo use case to set resource limits, https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
      //    val resourceLimits = new V1ResourceRequirements()
      //    resourceLimits.setLimits(Map("memory" -> "64Mi", "cpu" -> "500m"))
      //    v1Container.resources(resourceLimits)

      container.ports.map(ports => v1Container.setPorts(ports.map(_.getJavaSerialization).toList.asJava))

      v1Container
    }
  }

  implicit val kubernetesPodSerializable = new JavaSerializable[KubernetesPod, V1Pod] {
    def getJavaSerialization(pod: KubernetesPod): V1Pod = {
      val v1Pod = new V1Pod()
      val podMetadata = pod.name.getJavaSerialization
      podMetadata.labels(
        pod.selector.labels.asJava
      )

      val podSpec = new V1PodSpec()
      podSpec.containers(
        pod.containers.map(_.getJavaSerialization).toList.asJava
      )

      v1Pod.getSpec

      v1Pod.metadata(podMetadata)
      v1Pod.spec(podSpec)
      v1Pod.kind(KubernetesConstants.DEFAULT_POD_KIND)

      v1Pod
    }
  }

  implicit val servicePortSerializable = new JavaSerializable[ServicePort, V1ServicePort] {
    def getJavaSerialization(servicePort: ServicePort): V1ServicePort = {
      val v1Port = new V1ServicePort()
      v1Port.port(servicePort.value)

      v1Port
    }
  }

  implicit val kubernetesServiceKindSerializable = new JavaSerializable[KubernetesServiceKind, V1Service] {
    def getJavaSerialization(serviceKind: KubernetesServiceKind): V1Service = {
      val v1Service = new V1Service()
      v1Service.setKind(KubernetesConstants.SERVICE_KIND) //may not be necessary
      v1Service.setMetadata(serviceKind.serviceName.getJavaSerialization)

      val serviceSpec = new V1ServiceSpec()
      serviceSpec.ports(serviceKind.ports.map(_.getJavaSerialization).toList.asJava)
      serviceSpec.selector(serviceKind.selector.labels.asJava)
      serviceSpec.setType(serviceKind.kindName.value)
      //if we ever enter a scenario where the service acts as a load-balancer to multiple pods, this ensures that clients stick with the container that they initially connected with
      serviceSpec.setSessionAffinity(KubernetesConstants.STICKY_SESSION_AFFINITY)
      v1Service.setSpec(serviceSpec)

      v1Service
    }
  }
}

final case class JavaSerializableOps[A, B](a: A)(implicit ev: JavaSerializable[A, B]) {
  def getJavaSerialization: B = ev.getJavaSerialization(a)
}

object JavaSerializableSyntax {
  implicit def javaSerializableSyntax[A, B](a: A)(implicit ev: JavaSerializable[A, B]): JavaSerializableOps[A, B] =
    JavaSerializableOps[A, B](a)
}

// Models for the kubernetes client not related to GKE
object KubernetesModels {

  final case class KubernetesNamespace(name: KubernetesNamespaceName)

  //consider using a replica set if you would like multiple autoscaling pods https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#replicaset-v1-apps
  final case class KubernetesPod(name: KubernetesPodName,
                                 containers: Set[KubernetesContainer],
                                 selector: KubernetesSelector)

  final case class Image(uri: String)

  //volumes can be added here
  final case class KubernetesContainer(name: KubernetesContainerName,
                                       image: Image,
                                       ports: Option[Set[ContainerPort]],
                                       resourceLimits: Option[Map[String, String]] = None)

  sealed trait KubernetesServiceKind extends Product with Serializable {
    val SERVICE_TYPE_NODEPORT = KubernetesServiceKindName("NodePort")
    val SERVICE_TYPE_LOADBALANCER = KubernetesServiceKindName("LoadBalancer")
    val SERVICE_TYPE_CLUSTERIP =  KubernetesServiceKindName("ClusterIP")

    def kindName: KubernetesServiceKindName
    def serviceName: KubernetesServiceName
    def selector: KubernetesSelector
    def ports: Set[ServicePort]
  }

  object KubernetesServiceKind {
    final case class KubernetesLoadBalancerService(selector: KubernetesSelector,
                                                   ports: Set[ServicePort],
                                                   serviceName: KubernetesServiceName)
        extends KubernetesServiceKind {
      val kindName = SERVICE_TYPE_LOADBALANCER
    }

    final case class KubernetesNodePortService(selector: KubernetesSelector,
                                               ports: Set[ServicePort],
                                               serviceName: KubernetesServiceName)
        extends KubernetesServiceKind {
      val kindName = SERVICE_TYPE_NODEPORT
    }

    final case class KubernetesClusterIPService(selector: KubernetesSelector,
                                                ports: Set[ServicePort],
                                                serviceName: KubernetesServiceName)
        extends KubernetesServiceKind {
      val kindName = SERVICE_TYPE_CLUSTERIP
    }

  }

  final case class ServicePort(value: Int)

  //container ports are primarily informational, not specifying them does  not prevent them from being exposed
  final case class ContainerPort(value: Int)

  final case class KubernetesSelector(labels: Map[String, String])

  final protected case class KubernetesServiceKindName(value: String)

  final case class KubernetesMasterIP(value: String) {
    val url = s"https://${value}"
  }

  final case class KubernetesClusterCaCert(value: String) {
    val base64Cert = Either.catchNonFatal(Base64.decodeBase64(value))
  }

}
