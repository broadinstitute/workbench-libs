package org.broadinstitute.dsde.workbench.google2

import java.util.UUID

import cats.effect.{Blocker, IO}
import cats.effect.concurrent.Semaphore
import cats.mtl.ApplicativeAsk
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

//TODO: migrate to a unit test
//TODO: investigate running minikube in a docker for unit/automation tests https://banzaicloud.com/blog/minikube-ci/
object Test {
  import scala.concurrent.ExecutionContext.global
  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit val traceId = ApplicativeAsk.const[IO, TraceId](TraceId(UUID.randomUUID()))
  implicit def logger = Slf4jLogger.getLogger[IO]
  val blocker = Blocker.liftExecutionContext(global)
  val semaphore = Semaphore[IO](10).unsafeRunSync

  val project = GoogleProject("broad-dsde-dev")
  val location =  Location("us-central1")
  val parent = Parent(project, location)
  val clusterName = KubernetesClusterName("c1")
  val nodePoolName = NodePoolName("nodepool1")

  val defaultNamespaceName = KubernetesNamespaceName("n2")

  val clusterId = KubernetesClusterId(project, location, clusterName)

  val credPath = "/Users/jcanas/Downloads/kube-broad-dsde-dev-key.json"
  val serviceResource = GoogleKubernetesService.resource(credPath, blocker, semaphore)

  def makeClusterId(name: String) = KubernetesClusterId(project, location, KubernetesClusterName(name))

  def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest) = {
    serviceResource.use { service =>
      service.createCluster(kubernetesClusterRequest)
    }
  }

  def callCreateCluster(clusterId: KubernetesClusterId = clusterId) = createCluster(KubernetesCreateClusterRequest(project, location,
    KubernetesConstants.getDefaultCluster(nodePoolName, clusterName)))

  def callDeleteCluster() =   serviceResource.use { service =>
    service.deleteCluster(KubernetesClusterId(project, location, clusterName))
  }

  def callGetCluster() = serviceResource.use { service =>
    service.getCluster(KubernetesClusterId(project, location, clusterName))
  }

  val kubeService = for {
    gs <- GoogleKubernetesService.resource(credPath, blocker, semaphore)
    ks <-  KubernetesService.resource(credPath, gs, blocker, semaphore)
  } yield ks

  def callCreateNamespace(clusterId: KubernetesClusterId = clusterId, namespace: KubernetesNamespace = KubernetesNamespace(defaultNamespaceName)) = {
    kubeService.use { k =>
      k.createNamespace(clusterId, namespace)
    }
  }

  val DEFAULT_SERVICE_SELECTOR = KubernetesSelector(Map("user" -> "test-user"))

  val containers = Set(KubernetesContainer(KubernetesContainerName("container1"), Image("gcr.io/google-samples/node-hello:1.0"), None))
  val pod = KubernetesPod(KubernetesPodName("pod1"), containers, DEFAULT_SERVICE_SELECTOR)

  def callCreateService(clusterId: KubernetesClusterId = clusterId) = {
    kubeService.use { k =>
      k.createService(
        clusterId,
        KubernetesLoadBalancerService(
          DEFAULT_SERVICE_SELECTOR,
          KubernetesConstants.DEFAULT_LOADBALANCER_PORTS,
          KubernetesServiceName("s2")
        ),
        KubernetesNamespace(defaultNamespaceName)
      )
    }
  }

  def callCreatePod(clusterId: KubernetesClusterId = clusterId) = {
    kubeService.use { k =>
      k.createPod(
        clusterId,
        pod,
        KubernetesNamespace(defaultNamespaceName))
    }
  }

}
