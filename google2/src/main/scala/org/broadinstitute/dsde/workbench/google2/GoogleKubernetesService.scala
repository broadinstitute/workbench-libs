package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Async, ContextShift, IO, Resource, Timer}
import com.google.container.v1.Cluster
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.cloud.container.v1.{ClusterManagerClient, ClusterManagerSettings}
import com.google.container.v1.Operation
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

trait GoogleKubernetesService[F[_]] {
  def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest): F[Operation]

  def deleteCluster(clusterId: KubernetesClusterIdentifier): F[Operation]

  def getCluster(clusterId: KubernetesClusterIdentifier): F[Cluster]
}
//TODO: migrate to a unit test
object Test {
  import scala.concurrent.ExecutionContext.global
  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit def logger = Slf4jLogger.getLogger[IO]

  val project = GoogleProject("broad-dsde-dev")
  val location =  Location("us-central1")
  val parent = Parent(project, location)
  val clusterName = KubernetesClusterName("c2")
  val nodePoolName = NodePoolName("nodepool1")

  val cluster2Id = KubernetesClusterIdentifier(project, location, clusterName)

  val serviceResource = GoogleKubernetesService.resource("/Users/jcanas/Downloads/kube-broad-dsde-dev-key.json")
  val kubeService = KubernetesService.resource("/Users/jcanas/Downloads/kube-broad-dsde-dev-key.json", "/Users/jcanas/.kube/config", Set(cluster2Id))

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

  def testKubeClient_namespace() = {
    kubeService.use { k =>
      k.asInstanceOf[KubernetesInterpreter[IO]].createNamespace(KubernetesNamespace(KubernetesNamespaceName("test1")))
    }
  }

  val containers = Set(KubernetesContainer(KubernetesContainerName("container1"), Image("gcr.io/google-samples/node-hello:1.0"), None))
  val pod = KubernetesPod(KubernetesPodName("pod1"), containers, KubernetesConstants.DEFAULT_SERVICE_SELECTOR)

  def callCreateService() = {
    kubeService.use { k =>
      k.createService(KubernetesConstants.DEFAULT_LOADBALANCER_SERVICE)
    }
  }

  def callCreatePod() = {
    kubeService.use { k =>
      k.createPod(pod)
    }
  }
}

object GoogleKubernetesService {

  def resource[F[_] : StructuredLogger : Async : Timer : ContextShift](
                                                                        pathToCredential: String,
                                                                        retryConfig: RetryConfig = RetryPredicates.standardRetryConfig
                                                                      ): Resource[F, GoogleKubernetesService[F]] = {
    for {
      //service account used needs permissions
        //Kubernetes Cluster create/delete/get
        //service account user
      credential <- credentialResource(pathToCredential)
      credentialsProvider = FixedCredentialsProvider.create(credential)
      clusterManagerSettings = ClusterManagerSettings.newBuilder()
        //          .createClusterSettings()
        //          .setRetryableCodes() //TODO: investigate which codes should be labeled as retryable.
        .setCredentialsProvider(credentialsProvider)
        .build()
      clusterManager <- Resource.make(Async[F].delay(
        ClusterManagerClient.create(clusterManagerSettings)
      ))(client => Async[F].delay(client.shutdown()))
    } yield new GoogleKubernetesInterpreter[F](clusterManager, /*blocker, blockerBound,*/ retryConfig)
  }

}
