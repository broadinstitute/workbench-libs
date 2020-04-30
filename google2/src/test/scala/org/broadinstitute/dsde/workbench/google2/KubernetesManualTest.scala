package org.broadinstitute.dsde.workbench.google2

import java.nio.file.Paths
import java.util.UUID

import scala.concurrent.duration._
import cats.effect.{Blocker, IO}
import cats.effect.concurrent.Semaphore
import cats.mtl.ApplicativeAsk
import com.google.container.v1.{Cluster, Operation}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesConstants.{
  DEFAULT_NODEPOOL_AUTOSCALING,
  DEFAULT_NODEPOOL_SIZE
}
import org.broadinstitute.dsde.workbench.google2.KubernetesModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

//TODO: migrate to a unit test
//TODO: investigate running minikube in a docker for unit/automation tests https://banzaicloud.com/blog/minikube-ci/
final class Test(credPath: String,
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
    KubernetesName.withValidation[KubernetesNamespaceName](defaultNamespaceNameStr, KubernetesNamespaceName.apply)

  val clusterId = KubernetesClusterId(project, region, clusterName.right.get)

  val p = Paths.get(credPath)
  val serviceResource = GKEService.resource(p, blocker, semaphore)

  def makeClusterId(name: String) = KubernetesClusterId(project, region, KubernetesClusterName(name))

  def callCreateCluster(clusterId: KubernetesClusterId = clusterId): IO[Operation] = {
    val network: String = KubernetesNetwork(project, NetworkName(networkNameStr)).idString
    val cluster = KubernetesConstants
      .getDefaultCluster(nodepoolName.right.get, clusterName.right.get)
      .toBuilder
      .setNetwork(network) // needs to be a VPC network
      .setSubnetwork(KubernetesSubNetwork(project, RegionName(regionStr), SubnetworkName(subnetworkNameStr)).idString)
      .setNetworkPolicy(KubernetesConstants.getDefaultNetworkPolicy()) // needed for security
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
    val nodepoolConfig = NodepoolConfig(DEFAULT_NODEPOOL_SIZE, nodepoolName.right.get, DEFAULT_NODEPOOL_AUTOSCALING)
    val nodepool = KubernetesConstants.getNodepoolBuilder(nodepoolConfig).build()

    serviceResource.use { service =>
      service.createNodepool(KubernetesCreateNodepoolRequest(clusterId, nodepool))
    }
  }

  val kubeService = for {
    gs <- GKEService.resource(p, blocker, semaphore)
    ks <- KubernetesService.resource(p, gs, blocker, semaphore)
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
        _ <- k.createNamespace(clusterId, KubernetesNamespace(KubernetesNamespaceName("diff1")))
        _ <- k.createNamespace(clusterId, KubernetesNamespace(KubernetesNamespaceName("diff2")))
      } yield ()
    }

  val DEFAULT_SERVICE_SELECTOR = KubernetesSelector(Map("user" -> "test-user"))

  val containers = Set(
    KubernetesContainer(KubernetesContainerName("container1"), Image("gcr.io/google-samples/node-hello:1.0"), None)
  )
  val pod = KubernetesPod(KubernetesPodName("pod1"), containers, DEFAULT_SERVICE_SELECTOR)

  def callCreateService(clusterId: KubernetesClusterId = clusterId): IO[Unit] =
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

  def callCreatePod(clusterId: KubernetesClusterId = clusterId): IO[Unit] =
    kubeService.use { k =>
      k.createPod(clusterId, pod, KubernetesNamespace(defaultNamespaceName.right.get))
    }

  import org.broadinstitute.dsde.workbench.DoneCheckableSyntax._
  import org.broadinstitute.dsde.workbench.DoneCheckableInstances._
  def testPolling(operation: Operation): IO[Unit] =
    serviceResource.use { s =>
      for {
        lastOp <- s.pollOperation(KubernetesOperationId(project, region, operation), 5 seconds, 72).compile.lastOrError
        _ <- if (lastOp.isDone) IO(println(s"operation is done, initial operation: ${operation}"))
        else IO(s"operation errored, initial operation: ${operation}")
      } yield ()
    }
}
