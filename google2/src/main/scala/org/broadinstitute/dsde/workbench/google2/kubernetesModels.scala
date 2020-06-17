package org.broadinstitute.dsde.workbench.google2
import com.google.container.v1.Operation

import collection.JavaConverters._
import io.kubernetes.client.models.{
  V1Container,
  V1ContainerPort,
  V1Namespace,
  V1ObjectMeta,
  V1ObjectMetaBuilder,
  V1Pod,
  V1PodSpec,
  V1PolicyRule,
  V1Role,
  V1RoleBinding,
  V1RoleRef,
  V1Service,
  V1ServiceAccount,
  V1ServicePort,
  V1ServiceSpec,
  V1Subject
}
import org.apache.commons.codec.binary.Base64
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName._
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import cats.implicits._
import io.kubernetes.client.custom.IntOrString

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
    override lazy val toString: String = s"projects/${project.value}/locations/${location.value}"
  }

  //the cluster must have a name, and a com.google.container.v1.NodePool. The NodePool must have an initialNodeCount and a name.
  //the cluster must also have a network and subnetwork. See KubernetesManual test for how to specify these.
  //Location can either contain a zone or not, ex: "us-central1" or "us-central1-a". The former will create the nodepool you specify in multiple zones, the latter a single nodepool
  //see getDefaultCluster for an example of construction with the minimum fields necessary, plus some others you almost certainly want to configure
  final case class KubernetesCreateClusterRequest(project: GoogleProject,
                                                  location: Location,
                                                  cluster: com.google.container.v1.Cluster)

  final case class KubernetesCreateNodepoolRequest(clusterId: KubernetesClusterId,
                                                   nodepool: com.google.container.v1.NodePool)

  //this is NOT analogous to clusterName in the context of dataproc/GCE. A single cluster can have multiple nodes, pods, services, containers, deployments, etc.
  //clusters should most likely NOT be provisioned per user as they are today. More design/security research is needed
  final case class KubernetesClusterName(value: String) extends AnyVal

  final case class NodepoolAutoscalingConfig(minimumNodes: Int, maximumNodes: Int)

  final case class NodepoolName(value: String) extends AnyVal

  final case class NodepoolConfig(
    initialNodes: Int,
    name: NodepoolName,
    machineType: MachineTypeName,
    diskSize: Int,
    serviceAccount: ServiceAccountName,
    autoscalingConfig: NodepoolAutoscalingConfig
  )

  final case class KubernetesClusterId(project: GoogleProject, location: Location, clusterName: KubernetesClusterName) {
    override lazy val toString: String =
      s"projects/${project.value}/locations/${location.value}/clusters/${clusterName.value}"
  }

  final case class NodepoolId(clusterId: KubernetesClusterId, nodepoolName: NodepoolName) {
    override lazy val toString: String =
      s"${clusterId}/nodepools/${nodepoolName.value}"
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
  final case class NamespaceName(value: String) extends KubernetesSerializableName
  final case class ServiceAccountName(value: String) extends KubernetesSerializableName
  final case class RoleName(value: String) extends KubernetesSerializableName
  final case class RoleBindingName(value: String) extends KubernetesSerializableName
  final case class ServiceName(value: String) extends KubernetesSerializableName
  final case class ContainerName(value: String) extends KubernetesSerializableName
  final case class PodName(value: String) extends KubernetesSerializableName
}

trait JavaSerializable[A, B] {
  def getJavaSerialization(a: A): B
}

object JavaSerializableInstances {
  import KubernetesModels._
  import JavaSerializableSyntax._

  val DEFAULT_POD_KIND = "Pod"
  val SERVICE_KIND = "Service"
  // For session affinity, see https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#service-v1-core
  val STICKY_SESSION_AFFINITY = "ClientIP"

  private def getNameSerialization(name: KubernetesSerializableName): V1ObjectMeta = {
    val metadata = new V1ObjectMetaBuilder()
      .withNewName(name.value)
      .build()

    metadata
  }

  implicit val kubernetesNamespaceNameSerializable = new JavaSerializable[NamespaceName, V1ObjectMeta] {
    def getJavaSerialization(name: NamespaceName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesServiceAccountNameSerializable = new JavaSerializable[ServiceAccountName, V1ObjectMeta] {
    def getJavaSerialization(name: ServiceAccountName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesRoleNameSerializable = new JavaSerializable[RoleName, V1ObjectMeta] {
    def getJavaSerialization(name: RoleName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesRoleBindingNameSerializable = new JavaSerializable[RoleBindingName, V1ObjectMeta] {
    def getJavaSerialization(name: RoleBindingName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesPodNameSerializable = new JavaSerializable[PodName, V1ObjectMeta] {
    def getJavaSerialization(name: PodName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesContainerNameSerializable = new JavaSerializable[ContainerName, V1ObjectMeta] {
    def getJavaSerialization(name: ContainerName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesServiceNameSerializable = new JavaSerializable[ServiceName, V1ObjectMeta] {
    def getJavaSerialization(name: ServiceName): V1ObjectMeta = getNameSerialization(name)
  }

  implicit val kubernetesNamespaceSerializable = new JavaSerializable[KubernetesNamespace, V1Namespace] {
    def getJavaSerialization(kubernetesName: KubernetesNamespace): V1Namespace = {
      val v1Namespace = new V1Namespace()
      v1Namespace.metadata(kubernetesName.name.getJavaSerialization)
      v1Namespace
    }
  }

  implicit val kubernetesServiceAccountSerializable = new JavaSerializable[KubernetesServiceAccount, V1ServiceAccount] {
    def getJavaSerialization(sa: KubernetesServiceAccount): V1ServiceAccount = {
      val metadata = sa.name.getJavaSerialization
      metadata.annotations(sa.annotations.asJava)

      new V1ServiceAccount().metadata(metadata)
    }
  }

  implicit val kubernetesRoleSerializable = new JavaSerializable[KubernetesRole, V1Role] {
    def getJavaSerialization(role: KubernetesRole): V1Role = {
      val metadata = role.name.getJavaSerialization

      new V1Role()
        .rules(role.rules.asJava)
        .metadata(metadata)
    }
  }

  implicit val kubernetesRoleBindingSerializable = new JavaSerializable[KubernetesRoleBinding, V1RoleBinding] {
    def getJavaSerialization(rb: KubernetesRoleBinding): V1RoleBinding =
//      val metadata = rb.name.getJavaSerialization
//      metadata.annotations(rb.annotations.asJava)
//
//      new V1RoleBinding().metadata(metadata)
      new V1RoleBinding()
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
      v1Pod.kind(DEFAULT_POD_KIND)

      v1Pod
    }
  }

  implicit val servicePortSerializable = new JavaSerializable[ServicePort, V1ServicePort] {
    def getJavaSerialization(servicePort: ServicePort): V1ServicePort = {

      val v1Port = new V1ServicePort()
      val intOrString: IntOrString = new IntOrString(servicePort.targetPort.value)

      v1Port.port(servicePort.num.value)
      v1Port.setName(servicePort.name.value)
      v1Port.setProtocol(servicePort.protocol.value)
      v1Port.setTargetPort(intOrString)

      v1Port
    }
  }

  implicit val kubernetesServiceKindSerializable = new JavaSerializable[KubernetesServiceKind, V1Service] {
    def getJavaSerialization(serviceKind: KubernetesServiceKind): V1Service = {
      val v1Service = new V1Service()
      v1Service.setKind(SERVICE_KIND) //may not be necessary
      v1Service.setMetadata(serviceKind.serviceName.getJavaSerialization)

      val serviceSpec = new V1ServiceSpec()
      serviceSpec.ports(serviceKind.ports.map(_.getJavaSerialization).toList.asJava)
      serviceSpec.selector(serviceKind.selector.labels.asJava)
      serviceSpec.setType(serviceKind.kindName.value)
      //if we ever enter a scenario where the service acts as a load-balancer to multiple pods, this ensures that clients stick with the container that they initially connected with
      serviceSpec.setSessionAffinity(STICKY_SESSION_AFFINITY)
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
  final case class KubernetesNamespace(name: NamespaceName)
  final case class KubernetesServiceAccount(name: ServiceAccountName, annotations: Map[String, String])
  final case class KubernetesRole(name: RoleName, rules: List[V1PolicyRule])
  final case class KubernetesRoleBinding(name: RoleBindingName, roleRef: V1RoleRef, subjects: List[V1Subject])

  //consider using a replica set if you would like multiple autoscaling pods https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#replicaset-v1-apps
  final case class KubernetesPod(name: PodName, containers: Set[KubernetesContainer], selector: KubernetesSelector)

  final case class Image(uri: String)

  //volumes can be added here
  final case class KubernetesContainer(name: ContainerName,
                                       image: Image,
                                       ports: Option[Set[ContainerPort]],
                                       resourceLimits: Option[Map[String, String]] = None)

  sealed trait KubernetesServiceKind extends Product with Serializable {
    val SERVICE_TYPE_NODEPORT = KubernetesServiceKindName("NodePort")
    val SERVICE_TYPE_LOADBALANCER = KubernetesServiceKindName("LoadBalancer")
    val SERVICE_TYPE_CLUSTERIP = KubernetesServiceKindName("ClusterIP")

    def kindName: KubernetesServiceKindName
    def serviceName: ServiceName
    def selector: KubernetesSelector
    def ports: Set[ServicePort]
  }

  object KubernetesServiceKind {
    final case class KubernetesLoadBalancerService(selector: KubernetesSelector,
                                                   ports: Set[ServicePort],
                                                   serviceName: ServiceName)
        extends KubernetesServiceKind {
      val kindName = SERVICE_TYPE_LOADBALANCER
    }

    final case class KubernetesNodePortService(selector: KubernetesSelector,
                                               ports: Set[ServicePort],
                                               serviceName: ServiceName)
        extends KubernetesServiceKind {
      val kindName = SERVICE_TYPE_NODEPORT
    }

    final case class KubernetesClusterIPService(selector: KubernetesSelector,
                                                ports: Set[ServicePort],
                                                serviceName: ServiceName)
        extends KubernetesServiceKind {
      val kindName = SERVICE_TYPE_CLUSTERIP
    }

  }

  final case class ServicePort(num: PortNum, name: PortName, targetPort: TargetPortNum, protocol: Protocol)

  final case class PortNum(value: Int) extends AnyVal
  final case class TargetPortNum(value: Int) extends AnyVal
  final case class PortName(value: String) extends AnyVal
  final case class Protocol(value: String) extends AnyVal

  //container ports are primarily informational, not specifying them does not prevent them from being exposed
  final case class ContainerPort(value: Int)

  final case class KubernetesSelector(labels: Map[String, String])

  final case class KubernetesServiceKindName(value: String)

  final case class KubernetesApiServerIp(value: String) {
    val url = s"https://${value}"
  }

  final case class KubernetesClusterCaCert(value: String) {
    val base64Cert = Either.catchNonFatal(Base64.decodeBase64(value))
  }

}
