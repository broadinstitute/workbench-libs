package org.broadinstitute.dsde.workbench.google2

import java.nio.file.Paths
import java.util.UUID

import scala.concurrent.duration._
import cats.effect.{Blocker, IO}
import cats.effect.concurrent.Semaphore
import cats.mtl.ApplicativeAsk
import com.google.container.v1.Operation
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesConstants.DEFAULT_NETWORK_NAME
import org.broadinstitute.dsde.workbench.google2.KubernetesModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName._
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
  val clusterName = KubernetesName.withValidation[KubernetesClusterName]("c2", KubernetesClusterName.apply)
//  KubernetesName.fromString("c3", KubernetesClusterName.apply)
  val nodePoolName = KubernetesName.withValidation[NodePoolName]("nodepool1",NodePoolName.apply)

  val defaultNamespaceName = KubernetesName.withValidation[KubernetesNamespaceName]("n2", KubernetesNamespaceName.apply)

  val clusterId = KubernetesClusterId(project, location, clusterName.right.get)

  val credPath = "/Users/jcanas/Downloads/kube-broad-dsde-dev-key.json"
  val p = Paths.get(credPath)
  val serviceResource = GKEService.resource(p, blocker, semaphore)

  def makeClusterId(name: String) = KubernetesClusterId(project, location, KubernetesClusterName(name))

  def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest) = {
    serviceResource.use { service =>
      service.createCluster(kubernetesClusterRequest)
    }
  }

  def callCreateCluster(clusterId: KubernetesClusterId = clusterId) =  {
    val network: String = KubernetesNetwork(project, DEFAULT_NETWORK_NAME).idString
    val cluster = KubernetesConstants.getDefaultCluster(nodePoolName.right.get, clusterName.right.get)
      .toBuilder
      .setNetwork(network)
      .setSubnetwork(KubernetesSubNetwork(project, location, SubnetworkName("kube-test")).idString)
//      .setNetworkConfig(KubernetesConstants.getDefaultNetwork(project))
      .setNetworkPolicy(KubernetesConstants.getDefaultNetworkPolicy())
      .build()
       createCluster(KubernetesCreateClusterRequest(project, location, cluster))
    }

  def callDeleteCluster(clusterId: KubernetesClusterId = clusterId) =   serviceResource.use { service =>
    service.deleteCluster(KubernetesClusterId(project, location, clusterName.right.get))
  }

  def callGetCluster(clusterId: KubernetesClusterId = clusterId) = serviceResource.use { service =>
    service.getCluster(KubernetesClusterId(project, location, clusterName.right.get))
  }

  val kubeService = for {
    gs <- GKEService.resource(p, blocker, semaphore)
    ks <-  KubernetesService.resource(p, gs, blocker, semaphore)
  } yield ks

  def callCreateNamespace(clusterId: KubernetesClusterId = clusterId, namespace: KubernetesNamespace = KubernetesNamespace(defaultNamespaceName.right.get)) = {
    kubeService.use { k =>
      k.createNamespace(clusterId, namespace)
    }
  }

  def testGetClient(clusterId: KubernetesClusterId = clusterId) = {
    kubeService.use { k =>
      for {
        _ <- k.createNamespace(clusterId, KubernetesNamespace(KubernetesNamespaceName("diff1")))
        _ <- k.createNamespace(clusterId, KubernetesNamespace(KubernetesNamespaceName("diff2")))
      } yield ()
    }
  }

  val DEFAULT_SERVICE_SELECTOR = KubernetesSelector(Map("user" -> "test-user"))

  val containers = Set(KubernetesContainer(KubernetesContainerName("container1"), Image("gcr.io/google-samples/node-hello:1.0"), None))
  val pod = KubernetesPod(KubernetesPodName("pod1"), containers, DEFAULT_SERVICE_SELECTOR)

  def callCreateService(clusterId: KubernetesClusterId = clusterId) = {
    kubeService.use { k =>
      k.createService(
        clusterId,
        KubernetesServiceKind.KubernetesLoadBalancerService(
          DEFAULT_SERVICE_SELECTOR,
          KubernetesConstants.DEFAULT_LOADBALANCER_PORTS,
          KubernetesServiceName("s3")
        ),
        KubernetesNamespace(defaultNamespaceName.right.get)
      )
    }
  }

  def callCreatePod(clusterId: KubernetesClusterId = clusterId) = {
    kubeService.use { k =>
      k.createPod(
        clusterId,
        pod,
        KubernetesNamespace(defaultNamespaceName.right.get))
    }
  }

  import org.broadinstitute.dsde.workbench.DoneCheckableSyntax._
  import org.broadinstitute.dsde.workbench.DoneCheckableInstances._
  def testPolling(operation: Operation) = {
    serviceResource.use { s =>
      for {
        lastOp <- s.pollOperation(KubernetesOperationId(project, location, operation), 5 seconds, 72)
            .compile
            .lastOrError
        _ <- if (lastOp.isDone) IO(println(s"operation is done, initial operation: ${operation}")) else IO(s"operation errored, initial operation: ${operation}")
      } yield ()
    }
  }
}
