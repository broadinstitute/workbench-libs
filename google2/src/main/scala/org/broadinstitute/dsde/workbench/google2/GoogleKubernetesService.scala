package org.broadinstitute.dsde.workbench.google2

import java.util.UUID

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, IO, Resource, Timer}
import cats.mtl.ApplicativeAsk
import com.google.container.v1.Cluster
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.cloud.container.v1.{ClusterManagerClient, ClusterManagerSettings}
import com.google.container.v1.Operation
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.KubernetesConstants.DEFAULT_LOADBANCER_PORTS
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._

trait GoogleKubernetesService[F[_]] {
  //should clusters be created per project, or per user for billing?
  def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest)
                   (implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def deleteCluster(clusterId: KubernetesClusterIdentifier)
                   (implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def getCluster(clusterId: KubernetesClusterIdentifier)
                (implicit ev: ApplicativeAsk[F, TraceId]): F[Cluster]
}

//TODO: migrate to a unit test
object Test {
  import scala.concurrent.ExecutionContext.global
  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit val traceId = ApplicativeAsk.const[IO, TraceId](TraceId(UUID.randomUUID()))
  implicit def logger = Slf4jLogger.getLogger[IO]
  val blocker = Blocker.liftExecutionContext(global)
  val semaphore = Semaphore[IO](1).unsafeRunSync

  val project = GoogleProject("broad-dsde-dev")
  val location =  Location("us-central1")
  val parent = Parent(project, location)
  val clusterName = KubernetesClusterName("c5")
  val nodePoolName = NodePoolName("nodepool1")

  val cluster2Id = KubernetesClusterIdentifier(project, location, clusterName)

  val serviceResource = GoogleKubernetesService.resource("/Users/jcanas/Downloads/kube-broad-dsde-dev-key.json", blocker, semaphore)
  val kubeService = KubernetesService.resource("/Users/jcanas/Downloads/kube-broad-dsde-dev-key.json", "/Users/jcanas/.kube/config", cluster2Id, blocker, semaphore)

  def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest) = {
    serviceResource.use { service =>
      service.createCluster(kubernetesClusterRequest)
    }
  }

  def callCreateCluster() = createCluster(KubernetesCreateClusterRequest(cluster2Id, nodePoolName, None))

  def callDeleteCluster() =   serviceResource.use { service =>
    service.deleteCluster(KubernetesClusterIdentifier(project, location, clusterName))
  }

  def callGetCluster() = serviceResource.use { service =>
    service.getCluster(KubernetesClusterIdentifier(project, location, clusterName))
  }

  def callCreateNamespace() = {
    kubeService.use { k =>
      k.asInstanceOf[KubernetesInterpreter[IO]].createNamespace(KubernetesNamespace(KubernetesNamespaceName("test2")))
    }
  }

  val DEFAULT_SERVICE_SELECTOR = KubernetesSelector(Map("user" -> "test-user"))

  val containers = Set(KubernetesContainer(KubernetesContainerName("container1"), Image("gcr.io/google-samples/node-hello:1.0"), None))
  val pod = KubernetesPod(KubernetesPodName("pod1"), containers, DEFAULT_SERVICE_SELECTOR)

  def callCreateService() = {
    kubeService.use { k =>
        k.createService(KubernetesLoadBalancerService(DEFAULT_SERVICE_SELECTOR, DEFAULT_LOADBANCER_PORTS, KubernetesServiceName("s1")), KubernetesNamespace(KubernetesNamespaceName("test2")))
    }
  }

  def callCreatePod() = {
    kubeService.use { k =>
      k.createPod(pod, KubernetesNamespace(KubernetesNamespaceName("test2")))
    }
  }
}

object GoogleKubernetesService {

  def resource[F[_] : StructuredLogger : Async : Timer : ContextShift](
                                                                        pathToCredential: String,
                                                                        blocker: Blocker,
                                                                        blockerBound: Semaphore[F],
                                                                        retryConfig: RetryConfig = RetryPredicates.retryConfigWithPredicates(whenStatusCode(404))
                                                                      ): Resource[F, GoogleKubernetesService[F]] = {
    for {
      //service account used needs permissions
        //Kubernetes Cluster create/delete/get
        //service account user
      credential <- credentialResource(pathToCredential)
      credentialsProvider = FixedCredentialsProvider.create(credential)
      clusterManagerSettings = ClusterManagerSettings.newBuilder()
        .setCredentialsProvider(credentialsProvider)
        .build()
      clusterManager <- Resource.make(Async[F].delay(
        ClusterManagerClient.create(clusterManagerSettings)
      ))(client => Async[F].delay(client.shutdown()))
    } yield new GoogleKubernetesInterpreter[F](clusterManager, blocker, blockerBound, retryConfig)
  }

}
