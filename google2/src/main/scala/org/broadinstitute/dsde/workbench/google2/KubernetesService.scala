package org.broadinstitute.dsde.workbench.google2

import java.io.FileReader

import sys.process._
import cats.effect.{Async, ContextShift, Resource, Timer}
import cats.implicits._
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.{KubernetesConstants, KubernetesNamespace, KubernetesPod, KubernetesServiceKind, RetryPredicates}
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.Configuration
import io.kubernetes.client.util.{ClientBuilder, KubeConfig}

trait KubernetesService[F[_]] {
  //namespaces group resources, and allow our list/get/update API calls to be segmented. This can be used on a per-user basis, for example
  def createNamespace(namespace: KubernetesNamespace): F[Unit]

  //pods represent a set of containers
  def createPod(pod: KubernetesPod, namespace: KubernetesNamespace = KubernetesConstants.DEFAULT_NAMESPACE): F[Unit]

  //certain services allow us to expose various containers via a matching selector
  def createService(service: KubernetesServiceKind, namespace: KubernetesNamespace = KubernetesConstants.DEFAULT_NAMESPACE): F[Unit]
}

object KubernetesService {
  def resource[F[_]: StructuredLogger : Async : Timer : ContextShift](
                                                                        pathToCredential: String,
                                                                        //TODO: preferably, this should be set via KUBECONFIG env var, which  See https://cloud.google.com/sdk/gcloud/reference/container/clusters/get-credentials
                                                                        pathToKubeConfig: String,
                                                                        clusterIds: Set[KubernetesClusterIdentifier],
                                                                        retryConfig: RetryConfig = RetryPredicates.standardRetryConfig
//                                                                      ): Resource[F, KubernetesService[F]] =
                                                                     ): Resource[F,KubernetesService[F]] =
    for {
      _ <- Resource.liftF(performGKEtoKubernetesCLIAuthHandoff(pathToCredential, clusterIds))
    fileReader <- Resource.make(
      Async[F].delay(new FileReader(pathToKubeConfig))
    )(reader => Async[F].delay(reader.close()))
    apiClient <- Resource.liftF(Async[F].delay(ClientBuilder.kubeconfig(
      KubeConfig.loadKubeConfig(fileReader)
    ).build()))
    _ = Configuration.setDefaultApiClient(apiClient)
    kubernetesApi = new CoreV1Api()
  } yield new KubernetesInterpreter(kubernetesApi, retryConfig)

  private def performGKEtoKubernetesCLIAuthHandoff[F[_] : Async](pathToCredential: String,  clusterIds: Set[KubernetesClusterIdentifier]): F[Unit] =
    for {
      _ <- Async[F].delay("gcloud config set container/use_application_default_credentials true".!)
      _ <- Async[F].delay(s"gcloud auth activate-service-account --key-file=${pathToCredential}".!)
      //The below line is where google magically sets up the credentials to a GKE cluster in our local kubernetes config file for us, see https://cloud.google.com/sdk/gcloud/reference/container/clusters/get-credentials
      _ <- Async[F].delay(clusterIds.foreach(clusterId =>
        s"gcloud container clusters get-credentials --project ${clusterId.project.value} --region ${clusterId.location.value} ${clusterId.clusterName.value}".!
      ))
      //This is necessary to do some setup that the Kubernetes client is expecting
      _ <- Async[F].delay("kubectl get secrets".!)
    } yield ()

}
