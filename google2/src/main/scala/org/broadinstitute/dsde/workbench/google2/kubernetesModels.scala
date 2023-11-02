package org.broadinstitute.dsde.workbench.google2

import collection.JavaConverters._
import io.kubernetes.client.openapi.models.{
  V1Container,
  V1ContainerPort,
  V1Namespace,
  V1ObjectMeta,
  V1Pod,
  V1PodSpec,
  V1PolicyRule,
  V1Role,
  V1RoleBinding,
  V1RoleRef,
  V1Secret,
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
import cats.syntax.all._
import io.kubernetes.client.custom.IntOrString
import ca.mrvisser.sealerate

/** Common Kubernetes models */
final case class KubernetesClusterNotFoundException(message: String) extends WorkbenchException {
  override def getMessage: String =
    s"$message. If you are a user, please contact support. It is possible your cluster was cleaned up."
}

final case class KubernetesInvalidNameException(message: String) extends WorkbenchException {
  override def getMessage: String = message
}

object KubernetesName {
  def withValidation[A](str: String, apply: String => A): Either[Throwable, A] = {
    val regex =
      "(?:[a-z](?:[-a-z0-9]{0,38}[a-z0-9])?)".r // this is taken directly from the google error message if you have an invalid nodepool name. Its not in the docs anywhere
    val isValidName: Boolean = regex.pattern.matcher(str).matches()

    if (isValidName) {
      Right(apply(str))
    } else {
      Left(
        KubernetesInvalidNameException(
          s"Kubernetes names must adhere to the following regex: $regex, you passed: $str."
        )
      )
    }
  }
}

// Google kubernetes client models //
object GKEModels {
  // "us-central1" is an example of a valid location.
  final case class Parent(project: GoogleProject, location: Location) {
    override lazy val toString: String = s"projects/${project.value}/locations/${location.value}"
  }

  // the cluster must have a name, and a NodePool. The NodePool must have an initialNodeCount and a name.
  // the cluster must also have a network and subnetwork. See KubernetesManual test for how to specify these.
  // Location can either contain a zone or not, ex: "us-central1" or "us-central1-a". The former will create the nodepool you specify in multiple zones, the latter a single nodepool
  // see getDefaultCluster for an example of construction with the minimum fields necessary, plus some others you almost certainly want to configure
  final case class KubernetesCreateClusterRequest(project: GoogleProject,
                                                  location: Location,
                                                  cluster: com.google.api.services.container.model.Cluster
  )

  final case class KubernetesCreateNodepoolRequest(clusterId: KubernetesClusterId,
                                                   nodepool: com.google.container.v1.NodePool
  )

  // this is NOT analogous to clusterName in the context of dataproc/GCE. A single cluster can have multiple nodes, pods, services, containers, deployments, etc.
  // clusters should most likely NOT be provisioned per user as they are today. More design/security research is needed
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
      s"$clusterId/nodepools/${nodepoolName.value}"
  }

  final case class KubernetesOperationId(project: GoogleProject, location: Location, operationName: String) {
    val idString: String = s"projects/${project.value}/locations/${location.value}/operations/$operationName"
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
  // this nesting of NamespaceName is necessary to prevent duplicating the code achieved by KubernetesSerializableName and KubernetesSerializable
  // namespaces also have criteria other than their name
  final case class NamespaceName(value: String) extends KubernetesSerializableName
  final case class ServiceAccountName(value: String) extends KubernetesSerializableName
  final case class ServiceName(value: String) extends KubernetesSerializableName
  final case class ContainerName(value: String) extends KubernetesSerializableName
  final case class PodName(value: String) extends KubernetesSerializableName

  final case class ApiGroupName(value: String) extends KubernetesSerializableName
  final case class ResourceName(value: String) extends KubernetesSerializableName
  final case class VerbName(value: String) extends KubernetesSerializableName
  final case class RoleName(value: String) extends KubernetesSerializableName
  final case class SubjectKindName(value: String) extends KubernetesSerializableName
  final case class RoleBindingName(value: String) extends KubernetesSerializableName
  final case class SecretName(value: String) extends KubernetesSerializableName
  final case class SecretKey(value: String) extends KubernetesSerializableName
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
  sealed trait KubernetesSessionAffinityEnum extends Product with Serializable {
    def value: String
  }

  object KubernetesSessionAffinityEnum {
    case object clientIp extends KubernetesSessionAffinityEnum {
      override def value: String = "ClientIP"
    }
  }
  val STICKY_SESSION_AFFINITY = KubernetesSessionAffinityEnum.clientIp.value

  private def getNameSerialization(name: KubernetesSerializableName): V1ObjectMeta =
    new V1ObjectMeta().name(name.value)

  /** Serializable name objects */
  implicit val kubernetesNamespaceNameSerializable: JavaSerializable[NamespaceName, V1ObjectMeta] =
    new JavaSerializable[NamespaceName, V1ObjectMeta] {
      def getJavaSerialization(name: NamespaceName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesServiceAccountNameSerializable: JavaSerializable[ServiceAccountName, V1ObjectMeta] =
    new JavaSerializable[ServiceAccountName, V1ObjectMeta] {
      def getJavaSerialization(name: ServiceAccountName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesPodNameSerializable: JavaSerializable[PodName, V1ObjectMeta] =
    new JavaSerializable[PodName, V1ObjectMeta] {
      def getJavaSerialization(name: PodName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesSecretNameSerializable: JavaSerializable[SecretName, V1ObjectMeta] =
    new JavaSerializable[SecretName, V1ObjectMeta] {
      def getJavaSerialization(name: SecretName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesContainerNameSerializable: JavaSerializable[ContainerName, V1ObjectMeta] =
    new JavaSerializable[ContainerName, V1ObjectMeta] {
      def getJavaSerialization(name: ContainerName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesServiceNameSerializable: JavaSerializable[ServiceName, V1ObjectMeta] =
    new JavaSerializable[ServiceName, V1ObjectMeta] {
      def getJavaSerialization(name: ServiceName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesApiGroupNameSerializable: JavaSerializable[ApiGroupName, V1ObjectMeta] =
    new JavaSerializable[ApiGroupName, V1ObjectMeta] {
      def getJavaSerialization(name: ApiGroupName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesResourceNameSerializable: JavaSerializable[ResourceName, V1ObjectMeta] =
    new JavaSerializable[ResourceName, V1ObjectMeta] {
      def getJavaSerialization(name: ResourceName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesVerbNameSerializable: JavaSerializable[VerbName, V1ObjectMeta] =
    new JavaSerializable[VerbName, V1ObjectMeta] {
      def getJavaSerialization(name: VerbName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesRoleNameSerializable: JavaSerializable[RoleName, V1ObjectMeta] =
    new JavaSerializable[RoleName, V1ObjectMeta] {
      def getJavaSerialization(name: RoleName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesSubjectKindNameSerializable: JavaSerializable[SubjectKindName, V1ObjectMeta] =
    new JavaSerializable[SubjectKindName, V1ObjectMeta] {
      def getJavaSerialization(name: SubjectKindName): V1ObjectMeta = getNameSerialization(name)
    }

  implicit val kubernetesRoleBindingNameSerializable: JavaSerializable[RoleBindingName, V1ObjectMeta] =
    new JavaSerializable[RoleBindingName, V1ObjectMeta] {
      def getJavaSerialization(name: RoleBindingName): V1ObjectMeta = getNameSerialization(name)
    }

  /** Serializable container objects corresponding to the names above */
  implicit val kubernetesNamespaceSerializable: JavaSerializable[KubernetesNamespace, V1Namespace] =
    new JavaSerializable[KubernetesNamespace, V1Namespace] {
      def getJavaSerialization(kubernetesName: KubernetesNamespace): V1Namespace = {
        val v1Namespace = new V1Namespace()
        v1Namespace.setMetadata(kubernetesName.name.getJavaSerialization)
        v1Namespace
      }
    }

  implicit val kuberrnetesSecretSerializable: JavaSerializable[KubernetesSecret, V1Secret] =
    new JavaSerializable[KubernetesSecret, V1Secret] {
      def getJavaSerialization(a: KubernetesSecret): V1Secret = {
        val v1Secret = new V1Secret()
        v1Secret.setMetadata(a.name.getJavaSerialization)
        v1Secret.setData(a.secrets.map { case (k, v) => (k.value, v) }.asJava)
        v1Secret.setType(a.secretType.toString)

        v1Secret
      }
    }

  implicit val kubernetesServiceAccountSerializable: JavaSerializable[KubernetesServiceAccount, V1ServiceAccount] =
    new JavaSerializable[KubernetesServiceAccount, V1ServiceAccount] {
      def getJavaSerialization(sa: KubernetesServiceAccount): V1ServiceAccount = {
        val metadata = sa.name.getJavaSerialization
        metadata.annotations(sa.annotations.asJava)

        new V1ServiceAccount().metadata(metadata)
      }
    }

  implicit val containerPortSerializable: JavaSerializable[ContainerPort, V1ContainerPort] =
    new JavaSerializable[ContainerPort, V1ContainerPort] {
      def getJavaSerialization(containerPort: ContainerPort): V1ContainerPort = {
        val v1Port = new V1ContainerPort()
        v1Port.containerPort(containerPort.value)
      }
    }

  implicit val kubernetesContainerSerializable: JavaSerializable[KubernetesContainer, V1Container] =
    new JavaSerializable[KubernetesContainer, V1Container] {
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

  implicit val kubernetesPodSerializable: JavaSerializable[KubernetesPod, V1Pod] =
    new JavaSerializable[KubernetesPod, V1Pod] {
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

  implicit val servicePortSerializable: JavaSerializable[ServicePort, V1ServicePort] =
    new JavaSerializable[ServicePort, V1ServicePort] {
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

  implicit val kubernetesServiceKindSerializable: JavaSerializable[KubernetesServiceKind, V1Service] =
    new JavaSerializable[KubernetesServiceKind, V1Service] {
      def getJavaSerialization(serviceKind: KubernetesServiceKind): V1Service = {
        val v1Service = new V1Service()
        v1Service.setKind(SERVICE_KIND) // may not be necessary
        v1Service.setMetadata(serviceKind.serviceName.getJavaSerialization)

        val serviceSpec = new V1ServiceSpec()
        serviceSpec.ports(serviceKind.ports.map(_.getJavaSerialization).toList.asJava)
        serviceSpec.selector(serviceKind.selector.labels.asJava)
        serviceSpec.setType(serviceKind.kindName.value)
        // if we ever enter a scenario where the service acts as a load-balancer to multiple pods, this ensures that clients stick with the container that they initially connected with
        serviceSpec.setSessionAffinity(STICKY_SESSION_AFFINITY)
        v1Service.setSpec(serviceSpec)

        v1Service
      }
    }

  implicit val kubernetesPolicyRuleSerializable: JavaSerializable[KubernetesPolicyRule, V1PolicyRule] =
    new JavaSerializable[KubernetesPolicyRule, V1PolicyRule] {
      def getJavaSerialization(policyRule: KubernetesPolicyRule): V1PolicyRule =
        new V1PolicyRule()
          .apiGroups(policyRule.apiGroups.toList.map(_.name).map(_.value).asJava)
          .resources(policyRule.resources.toList.map(_.name).map(_.value).asJava)
          .verbs(policyRule.verbs.toList.map(_.name).map(_.value).asJava)
    }

  implicit val kubernetesRoleSerializable: JavaSerializable[KubernetesRole, V1Role] =
    new JavaSerializable[KubernetesRole, V1Role] {
      def getJavaSerialization(role: KubernetesRole): V1Role =
        new V1Role()
          .metadata(role.name.getJavaSerialization)
          .rules(role.rules.map(_.getJavaSerialization).asJava)
    }

  implicit val kubernetesSubjectSerializable: JavaSerializable[KubernetesSubject, V1Subject] =
    new JavaSerializable[KubernetesSubject, V1Subject] {
      def getJavaSerialization(subject: KubernetesSubject): V1Subject =
        new V1Subject()
          .kind(subject.kind.toString)
          .name(subject.kindName.value)
          .namespace(subject.namespaceName.value)
    }

  implicit val kubernetesRoleRefSerializable: JavaSerializable[KubernetesRoleRef, V1RoleRef] =
    new JavaSerializable[KubernetesRoleRef, V1RoleRef] {
      def getJavaSerialization(roleRef: KubernetesRoleRef): V1RoleRef =
        new V1RoleRef()
          .apiGroup(roleRef.apiGroupName.value)
          .kind(roleRef.roleRefKind.toString)
          .name(roleRef.roleName.value)
    }

  implicit val kubernetesRoleBindingSerializable: JavaSerializable[KubernetesRoleBinding, V1RoleBinding] =
    new JavaSerializable[KubernetesRoleBinding, V1RoleBinding] {
      def getJavaSerialization(roleBinding: KubernetesRoleBinding): V1RoleBinding =
        new V1RoleBinding()
          .metadata(roleBinding.name.getJavaSerialization)
          .subjects(roleBinding.subjects.map(_.getJavaSerialization).asJava)
          .roleRef(roleBinding.roleRef.getJavaSerialization)
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
  final case class KubernetesNamespace(name: NamespaceName) extends AnyVal
  final case class KubernetesDeployment(value: String) extends AnyVal
  final case class KubernetesServiceAccount(name: ServiceAccountName, annotations: Map[String, String])

  // consider using a replica set if you would like multiple autoscaling pods https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#replicaset-v1-apps
  final case class KubernetesPod(name: PodName, containers: Set[KubernetesContainer], selector: KubernetesSelector)

  final case class KubernetesPodStatus(name: PodName, podStatus: PodStatus)

  final case class Image(uri: String)

  // volumes can be added here
  final case class KubernetesContainer(name: ContainerName,
                                       image: Image,
                                       ports: Option[Set[ContainerPort]],
                                       resourceLimits: Option[Map[String, String]] = None
  )

  sealed trait KubernetesServiceTypeEnum extends Product with Serializable {
    def value: String
  }

  object KubernetesServiceTypeEnum {
    case object clusterIp extends KubernetesServiceTypeEnum {
      override def value: String = "ClusterIP"
    }
    case object nodePort extends KubernetesServiceTypeEnum {
      override def value: String = "NodePort"
    }
    case object loadBalancer extends KubernetesServiceTypeEnum {
      override def value: String = "LoadBalancer"
    }
  }

  sealed trait KubernetesServiceKind extends Product with Serializable {
    def kindName: KubernetesServiceTypeEnum
    def serviceName: ServiceName
    def selector: KubernetesSelector
    def ports: Set[ServicePort]
  }

  object KubernetesServiceKind {
    final case class KubernetesLoadBalancerService(selector: KubernetesSelector,
                                                   ports: Set[ServicePort],
                                                   serviceName: ServiceName
    ) extends KubernetesServiceKind {
      val kindName = KubernetesServiceTypeEnum.loadBalancer
    }

    final case class KubernetesNodePortService(selector: KubernetesSelector,
                                               ports: Set[ServicePort],
                                               serviceName: ServiceName
    ) extends KubernetesServiceKind {
      val kindName = KubernetesServiceTypeEnum.nodePort
    }

    final case class KubernetesClusterIPService(selector: KubernetesSelector,
                                                ports: Set[ServicePort],
                                                serviceName: ServiceName
    ) extends KubernetesServiceKind {
      val kindName = KubernetesServiceTypeEnum.clusterIp
    }
  }

  sealed trait KubernetesServiceProtocolEnum extends Product with Serializable {
    def value: String
  }

  object KubernetesServiceProtocolEnum {
    case object tcp extends KubernetesServiceProtocolEnum {
      override def value: String = "TCP"
    }
  }

  final case class ServicePort(num: PortNum,
                               name: PortName,
                               targetPort: TargetPortNum,
                               protocol: KubernetesServiceProtocolEnum
  )

  final case class PortNum(value: Int) extends AnyVal
  final case class TargetPortNum(value: Int) extends AnyVal
  final case class PortName(value: String) extends AnyVal

  // container ports are primarily informational, not specifying them does not prevent them from being exposed
  final case class ContainerPort(value: Int)

  final case class KubernetesSelector(labels: Map[String, String])

  final case class KubernetesApiServerIp(value: String) {
    val url = s"https://$value"
  }

  final case class KubernetesClusterCaCert(value: String) {
    val base64Cert = Either.catchNonFatal(Base64.decodeBase64(value))
  }

  final case class KubernetesApiGroup(name: ApiGroupName)
  final case class KubernetesResource(name: ResourceName)
  final case class KubernetesVerb(name: VerbName)
  final case class KubernetesPolicyRule(apiGroups: Set[KubernetesApiGroup],
                                        resources: Set[KubernetesResource],
                                        verbs: Set[KubernetesVerb]
  )
  final case class KubernetesRole(name: RoleName, rules: List[KubernetesPolicyRule])

  sealed trait KubernetesSubjectKind extends Product with Serializable
  object KubernetesSubjectKind {
    case object User extends KubernetesSubjectKind
    case object Group extends KubernetesSubjectKind
    case object ServiceAccount extends KubernetesSubjectKind
  }
  sealed trait KubernetesRoleRefKind extends Product with Serializable
  object KubernetesRoleRefKind {
    case object Role extends KubernetesRoleRefKind
    case object ClusterRole extends KubernetesRoleRefKind
  }
  final case class KubernetesSubject(kind: KubernetesSubjectKind,
                                     kindName: SubjectKindName,
                                     namespaceName: NamespaceName
  )
  final case class KubernetesRoleRef(apiGroupName: ApiGroupName, roleRefKind: KubernetesRoleRefKind, roleName: RoleName)
  final case class KubernetesRoleBinding(name: RoleBindingName,
                                         roleRef: KubernetesRoleRef,
                                         subjects: List[KubernetesSubject]
  )

  // Kubernetes pod phase is what we'll use to map to KubernetesPodStatus - phases include Pending, Running, Succeeded, Failed, Unknown (https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-phase)
  // When all pods are Running or Succeeded, an app is considered Ready in Leonardo.
  // If a pod returns type Failed, then an app is status Error in Leo. If at least one pod returns Pending, then an app is in Creating status in Leo.
  sealed trait PodStatus extends Product with Serializable {
    def asString: String
  }
  object PodStatus {
    case object Pending extends PodStatus {
      val asString = "Pending"
    }

    case object Running extends PodStatus {
      val asString = "Running"
    }

    case object Succeeded extends PodStatus {
      val asString = "Succeeded"
    }

    case object Failed extends PodStatus {
      val asString = "Failed"
    }

    case object Unknown extends PodStatus {
      val asString = "Unknown"
    }

    val stringToPodStatus: Map[String, PodStatus] =
      sealerate.collect[PodStatus].map(p => (p.asString, p)).toMap
  }

  sealed trait KubernetesSecretType extends Product with Serializable
  object KubernetesSecretType {
    case object Generic extends KubernetesSecretType {
      override def toString: String = "generic"
    }

    case object DockerRegistry extends KubernetesSecretType {
      override def toString: String = "docker-registry"
    }

    case object TLS extends KubernetesSecretType {
      override def toString: String = "tls"
    }
  }
  final case class KubernetesSecret(namespaceName: NamespaceName,
                                    name: SecretName,
                                    secrets: Map[SecretKey, Array[Byte]],
                                    secretType: KubernetesSecretType
  )

}

final case class PvName(asString: String) extends AnyVal
