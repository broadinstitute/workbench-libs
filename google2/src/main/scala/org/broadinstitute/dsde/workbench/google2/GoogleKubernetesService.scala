package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Async, ContextShift, IO, Resource, Timer}
import com.google.container.v1.Cluster
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.util.{Image, KubernetesConstants, KubernetesContainer, KubernetesContainerName, KubernetesNamespace, KubernetesNamespaceName, KubernetesPod, KubernetesPodName}
import org.broadinstitute.dsde.workbench.model.WorkbenchException

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.cloud.container.v1.{ClusterManagerClient, ClusterManagerSettings}
import com.google.container.v1.{ClusterAutoscaling, Operation}
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

trait GoogleKubernetesService[F[_]] {
  def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest): F[Operation]

  def deleteCluster(clusterId: KubernetesClusterIdentifier): F[Operation]

  def getCluster(clusterId: KubernetesClusterIdentifier): F[Cluster]

  def initAuthToCluster(clusterId: KubernetesClusterIdentifier): F[Unit]
}

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
      k.createService(KubernetesConstants.DEFAULT_SERVICE)
    }
  }

  def callCreatePod() = {
    kubeService.use { k =>
      k.createPod(pod)
    }
  }
}

object GoogleKubernetesService {

  val DEFAULT_NODEPOOL_SIZE: Int = 1
  val DEFAULT_NODEPOOL_AUTOSCALING: ClusterNodePoolAutoscalingConfig = ClusterNodePoolAutoscalingConfig(1, 10)

  def resource[F[_] : StructuredLogger : Async : Timer : ContextShift](
                                                                        pathToCredential: String,
                                                                        retryConfig: RetryConfig = RetryPredicates.standardRetryConfig
                                                                      ): Resource[F, GoogleKubernetesService[F]] = {
    for {
      //service account used needs permissions
        //Kubernetes Cluster create/delete
        //service account user
        //
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

//"us-central1" is an example of a valid location.
final case class Parent(project: GoogleProject, location: Location) {
  def parentString: String = s"projects/${project.value}/locations/${location.value}"
}

final case class KubernetesCreateClusterRequest(clusterId: KubernetesClusterIdentifier, initialNodePoolName: NodePoolName, clusterOpts: Option[KubernetesClusterOpts])

//TODO: determine what leo needs here
final case class KubernetesClusterOpts(autoscaling: ClusterAutoscaling, initialNodePoolSize: Int = GoogleKubernetesService.DEFAULT_NODEPOOL_SIZE)
//this is NOT analogous to clusterName in the context of dataproc/GCE. A single cluster can have multiple nodes and multiple pods.
//clusters should most likely NOT be provisioned per user as they are today. More design/security research is needed
final case class KubernetesClusterName(value: String)
final case class ClusterNodePoolAutoscalingConfig(minimumNodes: Int, maximumNodes: Int)


final case class NodePoolName(value: String) {
  val regex = "(?:[a-z](?:[-a-z0-9]{0,38}[a-z0-9])?)".r //this is taken directly from the google error message if you have an invalid nodepool name. Its not in the docs anywhere
  val isValidName: Boolean = regex.pattern.matcher(value).matches()
  if (!isValidName) {
    //the throwing of this exception here is assuming that leo will be the one controlling nodepool names, not users, and is to sanitize the data in this case class at construction as opposed to downstream
    throw KubernetesException(s"The NodePool name must match the regex ${regex}")
  }
}

final case class NodePoolConfig(initialNodes: Int, name: NodePoolName, autoscalingConfig: ClusterNodePoolAutoscalingConfig = GoogleKubernetesService.DEFAULT_NODEPOOL_AUTOSCALING)

final case class KubernetesException(message: String) extends WorkbenchException

final case class KubernetesClusterIdentifier(project: GoogleProject, location: Location, clusterName: KubernetesClusterName) {
  def idString: String = s"projects/${project.value}/locations/${location.value}/clusters/${clusterName.value}"
}

//final case class KubernetesGetClusterRequest(clusterId: KubernetesClusterIdentifier)
//
//final case class KubernetesDeleteClusterRequest(clusterId: KubernetesClusterIdentifier)

