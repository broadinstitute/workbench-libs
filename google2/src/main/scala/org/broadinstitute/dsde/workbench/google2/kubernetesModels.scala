package org.broadinstitute.dsde.workbench.google2.util
import collection.JavaConverters._
import io.kubernetes.client.models.{V1Container, V1ContainerPort, V1Namespace, V1ObjectMeta, V1ObjectMetaBuilder, V1Pod, V1PodSpec, V1Service, V1ServicePort, V1ServiceSpec}


object KubernetesConstants {
  //this default namespace is initialized automatically with a kubernetes environment, and we do not create it
  val DEFAULT_NAMESPACE = KubernetesNamespace(KubernetesNamespaceName("default"))
  //this is a default service you must create in your environment. This is mostly used for testing, and should possibly be initialized with createCluster if you wish to use it.
  val DEFAULT_SERVICE_NAME = KubernetesServiceName("defaultService")
  val DEFAULT_SERVICE_SELECTOR = KubernetesSelector(Map("user" -> "testUser"))
  val DEFAULT_SERVICE = KubernetesNodePortService(DEFAULT_SERVICE_SELECTOR, Set(ServicePort(8080)), DEFAULT_SERVICE_NAME)
}


sealed trait KubernetesSerializable {
  def getJavaSerialization: Any
}

//the V1ObjectMeta is generalized to provide both 'name' and 'labels', as well as other fields, for all kubernetes entities
sealed trait KubernetesSerializableName extends KubernetesSerializable {
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

    v1Pod.metadata(podMetadata)
    v1Pod.spec(podSpec)
    v1Pod.kind(DEFAULT_POD_KIND)

    v1Pod
  }
}

final case class KubernetesContainerName(value: String) extends KubernetesSerializableName
final case class Image(uri: String)

//volumes can be added here
final case class KubernetesContainer(name: KubernetesContainerName, image: Image, ports: Option[Set[ContainerPort]]) extends KubernetesSerializable {
  override def getJavaSerialization: V1Container = {
    val v1Container = new V1Container()
    v1Container.setName(name.value)
    v1Container.setImage(image.uri)

    ports.map( ports =>
      v1Container.setPorts(ports.map(_.getJavaSerialization).toList.asJava)
    )

    v1Container
  }
}


final case class KubernetesServiceName(value: String) extends KubernetesSerializableName
sealed trait KubernetesServiceKind extends KubernetesSerializable {
  val NODEPORT_SERVICE_TYPE = "NodePort"

  def serviceType: KubernetesServiceType
  def name: KubernetesServiceName

  override def getJavaSerialization: V1Service = {
    val v1Service = new V1Service()
    v1Service.setKind(serviceType.value)
    v1Service.setMetadata(name.getJavaSerialization)

    v1Service
  }
}

final case class KubernetesNodePortService(selector: KubernetesSelector, ports: Set[ServicePort], name: KubernetesServiceName) extends KubernetesServiceKind {
  val serviceType = KubernetesServiceType(NODEPORT_SERVICE_TYPE)

  override def getJavaSerialization: V1Service = {
    val v1Service = super.getJavaSerialization

    val serviceSpec = new V1ServiceSpec()

    ports.foreach(port => serviceSpec.addPortsItem(port.getJavaSerialization))
    serviceSpec.selector(selector.labels.asJava)
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
