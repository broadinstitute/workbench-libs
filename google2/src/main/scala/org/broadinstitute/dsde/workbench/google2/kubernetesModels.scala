package org.broadinstitute.dsde.workbench.google2
import com.google.container.v1.ClusterAutoscaling

import collection.JavaConverters._
import io.kubernetes.client.models.{V1Container, V1ContainerPort, V1Namespace, V1ObjectMeta, V1ObjectMetaBuilder, V1Pod, V1PodSpec, V1Service, V1ServicePort, V1ServiceSpec}
import org.apache.commons.codec.binary.Base64
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.broadinstitute.dsde.workbench.model.google.GoogleProject


object KubernetesConstants {
  //this default namespace is initialized automatically with a kubernetes environment, and we do not create it
  val DEFAULT_NAMESPACE = "default"
  //this is a default service you must create in your environment. This is mostly used for testing, and should possibly be initialized with createCluster if you wish to use it.
  val DEFAULT_SERVICE_NAME = "default-service"

  //composite of NodePort and ClusterIP types. Allows external access
  val DEFAULT_LOADBALANCER_PORTS = Set(ServicePort(8080))
//  val DEFAULT_LOADBALANCER_SERVICE = KubernetesLoadBalancerService(DEFAULT_SERVICE_SELECTOR, Set(ServicePort(8080)), DEFAULT_SERVICE_NAME)

  val DEFAULT_NODEPOOL_SIZE: Int = 1
  val DEFAULT_NODEPOOL_AUTOSCALING: ClusterNodePoolAutoscalingConfig = ClusterNodePoolAutoscalingConfig(1, 10)
}


// Common kubernetes models //

final case class KubernetesException(message: String) extends WorkbenchException

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


//"us-central1" is an example of a valid location.
final case class Parent(project: GoogleProject, location: Location) {
  def parentString: String = s"projects/${project.value}/locations/${location.value}"
}

final case class KubernetesCreateClusterRequest(clusterId: KubernetesClusterId, initialNodePoolName: NodePoolName, clusterOpts: Option[KubernetesClusterOpts])

//TODO: determine what leo needs here
final case class KubernetesClusterOpts(autoscaling: ClusterAutoscaling, initialNodePoolSize: Int = KubernetesConstants.DEFAULT_NODEPOOL_SIZE)
//this is NOT analogous to clusterName in the context of dataproc/GCE. A single cluster can have multiple nodes, pods, services, containers, deployments, etc.
//clusters should most likely NOT be provisioned per user as they are today. More design/security research is needed
final case class KubernetesClusterName(value: String) extends KubernetesNameValidation
final case class ClusterNodePoolAutoscalingConfig(minimumNodes: Int, maximumNodes: Int)

final case class NodePoolName(value: String) extends KubernetesNameValidation

final case class NodePoolConfig(initialNodes: Int, name: NodePoolName, autoscalingConfig: ClusterNodePoolAutoscalingConfig = KubernetesConstants.DEFAULT_NODEPOOL_AUTOSCALING)

final case class KubernetesClusterId(project: GoogleProject, location: Location, clusterName: KubernetesClusterName) {
  def idString: String = s"projects/${project.value}/locations/${location.value}/clusters/${clusterName.value}"
}


// Kubernetes client models //


sealed trait KubernetesSerializable {
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

//this nesting is necessary to prevent duplicating the code achieved by KubernetesSerializableName and KubernetesSerializable
//namespaces also have criteria other than their name
final case class KubernetesNamespaceName(value: String) extends KubernetesSerializableName

final case class KubernetesNamespace(name: KubernetesNamespaceName) extends KubernetesSerializable {
  override def getJavaSerialization: V1Namespace = {
    val v1Namespace = new V1Namespace()
    v1Namespace.metadata(name.getJavaSerialization)
    v1Namespace
  }
}

final case class KubernetesPodName(value: String)  extends KubernetesSerializableName

//consider using a replica set if you would like multiple autoscaling pods https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#replicaset-v1-apps
final case class KubernetesPod(name: KubernetesPodName, containers: Set[KubernetesContainer], selector: KubernetesSelector) extends KubernetesSerializable {

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

final case class KubernetesContainerName(value: String) extends KubernetesSerializableName
final case class Image(uri: String)

//volumes can be added here
final case class KubernetesContainer(name: KubernetesContainerName, image: Image, ports: Option[Set[ContainerPort]], resourceLimits: Option[Map[String, String]] = None) extends KubernetesSerializable {
  override def getJavaSerialization: V1Container = {
    val v1Container = new V1Container()
    v1Container.setName(name.value)
    v1Container.setImage(image.uri)

// example on leo use case to set resource limits, https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
//    val resourceLimits = new V1ResourceRequirements()
//    resourceLimits.setLimits(Map("memory" -> "64Mi", "cpu" -> "500m"))
//    v1Container.resources(resourceLimits)

    ports.map( ports =>
      v1Container.setPorts(ports.map(_.getJavaSerialization).toList.asJava)
    )

    v1Container
  }
}


final case class KubernetesServiceName(value: String) extends KubernetesSerializableName
sealed trait KubernetesServiceKind extends KubernetesSerializable {
  val NODEPORT_SERVICE_TYPE = "NodePort"
  val LOADBALANCER_SERVICE_TYPE = "LoadBalancer"
  val SERVICE_KIND = "Service"

  def serviceType: KubernetesServiceType
  def name: KubernetesServiceName

  override def getJavaSerialization: V1Service = {
    val v1Service = new V1Service()
    v1Service.setKind(SERVICE_KIND) //may not be necessary
    v1Service.setMetadata(name.getJavaSerialization)

    v1Service
  }
}

//duplicative of above, but commonalities not fully understood yet
final case class KubernetesLoadBalancerService(selector: KubernetesSelector, ports: Set[ServicePort], name: KubernetesServiceName) extends KubernetesServiceKind {
  val serviceType = KubernetesServiceType(LOADBALANCER_SERVICE_TYPE)

  override def getJavaSerialization: V1Service = {
    val v1Service = super.getJavaSerialization

    val serviceSpec = new V1ServiceSpec()

    serviceSpec.ports(ports.map(_.getJavaSerialization).toList.asJava)
    serviceSpec.selector(selector.labels.asJava)
    serviceSpec.setType(serviceType.value)
    v1Service.setSpec(serviceSpec)

    v1Service
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
protected final case class KubernetesServiceType(value: String)

final case class KubernetesMasterIP(value: String) {
  val url = s"https://${value}"
}

final case class KubernetesClusterCaCert(value: String) {
  val base64Cert = Base64.decodeBase64(value)
}
