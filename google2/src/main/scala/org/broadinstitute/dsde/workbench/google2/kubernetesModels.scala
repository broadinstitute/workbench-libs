package org.broadinstitute.dsde.workbench.google2
import com.google.container.v1.{Cluster, NodePool, NodePoolAutoscaling, Operation}

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

object KubernetesConstants {
  //this default namespace is initialized automatically with a kubernetes environment, and we do not create it
  val DEFAULT_NAMESPACE = "default"

  //composite of NodePort and ClusterIP types. Allows external access
  val DEFAULT_LOADBALANCER_PORTS = Set(ServicePort(8080))

  val DEFAULT_NODEPOOL_SIZE: Int = 1
  val DEFAULT_NODEPOOL_AUTOSCALING: ClusterNodePoolAutoscalingConfig = ClusterNodePoolAutoscalingConfig(1, 10)

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
      .setAutoscaling(
        NodePoolAutoscaling
          .newBuilder()
          .setEnabled(true)
          .setMinNodeCount(config.autoscalingConfig.minimumNodes)
          .setMaxNodeCount(config.autoscalingConfig.maximumNodes)
      )
}

// Common kubernetes models //

final case class KubernetesException(message: String) extends WorkbenchException
final case class KubernetesClusterNotFoundException(message: String) extends WorkbenchException

trait KubernetesNameValidation {
  def value: String

  val regex = "(?:[a-z](?:[-a-z0-9]{0,38}[a-z0-9])?)".r //this is taken directly from the google error message if you have an invalid nodepool name. Its not in the docs anywhere
  val isValidName: Boolean = regex.pattern.matcher(value).matches()
  if (!isValidName) {
    //the throwing of this exception here is assuming that leo will be the one controlling nodepool names, not users, and is to sanitize the data in this case class at construction as opposed to downstream
    throw KubernetesException(s"The name ${value} must match the regex ${regex}")
  }
}

// Google kubernetes client models //
object GKEModels {

  //"us-central1" is an example of a valid location.
  final case class Parent(project: GoogleProject, location: Location) {
    def parentString: String = s"projects/${project.value}/locations/${location.value}"
  }

  //the cluster must have a name, and a com.google.container.v1.NodePool. The NodePool must have an initialNodeCount and a name.
  //see getDefaultCluster for an example of construction with the minimum fields necessary, plus some others you almost certainly want to configure
  final case class KubernetesCreateClusterRequest(project: GoogleProject,
                                                  location: Location,
                                                  cluster: com.google.container.v1.Cluster)

  //this is NOT analogous to clusterName in the context of dataproc/GCE. A single cluster can have multiple nodes, pods, services, containers, deployments, etc.
  //clusters should most likely NOT be provisioned per user as they are today. More design/security research is needed
  final case class KubernetesClusterName(value: String) extends KubernetesNameValidation

  final case class ClusterNodePoolAutoscalingConfig(minimumNodes: Int, maximumNodes: Int)

  final case class NodePoolName(value: String) extends KubernetesNameValidation

  final case class NodePoolConfig(initialNodes: Int,
                                  name: NodePoolName,
                                  autoscalingConfig: ClusterNodePoolAutoscalingConfig =
                                    KubernetesConstants.DEFAULT_NODEPOOL_AUTOSCALING)

  final case class KubernetesClusterId(project: GoogleProject, location: Location, clusterName: KubernetesClusterName) {
    def idString: String = s"projects/${project.value}/locations/${location.value}/clusters/${clusterName.value}"
  }

  final case class KubernetesOperationId(project: GoogleProject, location: Location, operation: Operation) {
    def idString: String = s"projects/${project.value}/locations/${location.value}/operations/${operation.getName}"
  }

}

// Kubernetes client models and traits //
sealed trait KubernetesSerializable extends Product with Serializable {
  def getJavaSerialization: Any
}

//the V1ObjectMeta is generalized to provide both 'name' and 'labels', as well as other fields, for all kubernetes entities
sealed trait KubernetesSerializableName extends KubernetesSerializable with KubernetesNameValidation {
  def value: String

  def getJavaSerialization: V1ObjectMeta = {
    val metadata = new V1ObjectMetaBuilder()
      .withNewName(value)
      .build()

    metadata
  }
}

object KubernetesSerializableName {
  //this nesting is necessary to prevent duplicating the code achieved by KubernetesSerializableName and KubernetesSerializable
  //namespaces also have criteria other than their name
  final case class KubernetesNamespaceName(value: String) extends KubernetesSerializableName
  final case class KubernetesServiceName(value: String) extends KubernetesSerializableName
  final case class KubernetesContainerName(value: String) extends KubernetesSerializableName
  final case class KubernetesPodName(value: String) extends KubernetesSerializableName
}

// Models for the kubernetes client not related to GKE
object KubernetesModels {

  final case class KubernetesNamespace(name: KubernetesNamespaceName) extends KubernetesSerializable {
    override def getJavaSerialization: V1Namespace = {
      val v1Namespace = new V1Namespace()
      v1Namespace.metadata(name.getJavaSerialization)
      v1Namespace
    }
  }

  //consider using a replica set if you would like multiple autoscaling pods https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#replicaset-v1-apps
  final case class KubernetesPod(name: KubernetesPodName,
                                 containers: Set[KubernetesContainer],
                                 selector: KubernetesSelector)
      extends KubernetesSerializable {

    val DEFAULT_POD_KIND = "Pod"

    override def getJavaSerialization: V1Pod = {
      val v1Pod = new V1Pod()
      val podMetadata = name.getJavaSerialization
      podMetadata.labels(
        selector.labels.asJava
      )

      val podSpec = new V1PodSpec()
      podSpec.containers(
        containers.map(_.getJavaSerialization).toList.asJava
      )

      v1Pod.getSpec

      v1Pod.metadata(podMetadata)
      v1Pod.spec(podSpec)
      v1Pod.kind(DEFAULT_POD_KIND)

      v1Pod
    }
  }

  final case class Image(uri: String)

  //volumes can be added here
  final case class KubernetesContainer(name: KubernetesContainerName,
                                       image: Image,
                                       ports: Option[Set[ContainerPort]],
                                       resourceLimits: Option[Map[String, String]] = None)
      extends KubernetesSerializable {
    override def getJavaSerialization: V1Container = {
      val v1Container = new V1Container()
      v1Container.setName(name.value)
      v1Container.setImage(image.uri)

      // example on leo use case to set resource limits, https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
      //    val resourceLimits = new V1ResourceRequirements()
      //    resourceLimits.setLimits(Map("memory" -> "64Mi", "cpu" -> "500m"))
      //    v1Container.resources(resourceLimits)

      ports.map(ports => v1Container.setPorts(ports.map(_.getJavaSerialization).toList.asJava))

      v1Container
    }
  }

  sealed trait KubernetesServiceKind extends KubernetesSerializable {
    val SERVICE_TYPE_NODEPORT = "NodePort"
    val SERVICE_TYPE_LOADBALANCER = "LoadBalancer"
    val SERVICE_KIND = "Service"
    //for session affinity, see https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#service-v1-core
    val STICKY_SESSION_AFFINITY = "ClientIP"

    def serviceType: KubernetesServiceType
    def name: KubernetesServiceName
    def selector: KubernetesSelector
    def ports: Set[ServicePort]

    override def getJavaSerialization: V1Service = {
      val v1Service = new V1Service()
      v1Service.setKind(SERVICE_KIND) //may not be necessary
      v1Service.setMetadata(name.getJavaSerialization)

      val serviceSpec = new V1ServiceSpec()
      serviceSpec.ports(ports.map(_.getJavaSerialization).toList.asJava)
      serviceSpec.selector(selector.labels.asJava)
      serviceSpec.setType(serviceType.value)
      //if we ever enter a scenario where the service acts as a load-balancer to multiple pods, this ensures that clients stick with the container that they initially connected with
      serviceSpec.setSessionAffinity(STICKY_SESSION_AFFINITY)
      v1Service.setSpec(serviceSpec)

      v1Service
    }
  }

  object KubernetesServiceKind {

    final case class KubernetesLoadBalancerService(selector: KubernetesSelector,
                                                   ports: Set[ServicePort],
                                                   name: KubernetesServiceName)
        extends KubernetesServiceKind {
      val serviceType = KubernetesServiceType(SERVICE_TYPE_LOADBALANCER)
    }

    final case class KubernetesNodePortService(selector: KubernetesSelector,
                                               ports: Set[ServicePort],
                                               name: KubernetesServiceName)
        extends KubernetesServiceKind {
      val serviceType = KubernetesServiceType(SERVICE_TYPE_NODEPORT)
    }

  }

  final case class ServicePort(value: Int) extends KubernetesSerializable {
    override def getJavaSerialization: V1ServicePort = {
      val v1Port = new V1ServicePort()
      v1Port.port(value)

      v1Port
    }
  }

  //container ports are primarily informational, not specifying them does  not prevent them from being exposed
  final case class ContainerPort(value: Int) extends KubernetesSerializable {
    override def getJavaSerialization: V1ContainerPort = {
      val v1Port = new V1ContainerPort()
      v1Port.containerPort(value)
    }
  }

  final case class KubernetesSelector(labels: Map[String, String])

  final protected case class KubernetesServiceType(value: String)

  final case class KubernetesMasterIP(value: String) {
    val url = s"https://${value}"
  }

  final case class KubernetesClusterCaCert(value: String) {
    val base64Cert = Base64.decodeBase64(value)
  }

}
