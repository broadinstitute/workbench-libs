package org.broadinstitute.dsde.workbench.google2

import java.io.FileReader

import cats.effect.concurrent.Semaphore

import sys.process._
import cats.effect.{Async, Blocker, ContextShift, Resource, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.auth.oauth2.GoogleCredentials
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.Configuration
import io.kubernetes.client.util.{ClientBuilder, Config, KubeConfig}
import org.broadinstitute.dsde.workbench.google2.util.{ReplacedGCPAuthenticator, RetryPredicates}
import org.broadinstitute.dsde.workbench.model.TraceId

trait KubernetesService[F[_]] {
  //namespaces group resources, and allow our list/get/update API calls to be segmented. This can be used on a per-user basis, for example
  def createNamespace(namespace: KubernetesNamespace)
  (implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]

  //pods represent a set of containers
  def createPod(pod: KubernetesPod, namespace: KubernetesNamespace)
               (implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]

  //certain services allow us to expose various containers via a matching selector
  def createService(service: KubernetesServiceKind, namespace: KubernetesNamespace)
                   (implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]
}

object KubernetesService {
  //this should be released as soon as the necessary calls are made
  //TODO: this should block so only one resource is allowed at a time, unless better context switching is implemented
  def resource[F[_]: StructuredLogger : Async : Timer : ContextShift](
                                                                        pathToCredential: String,
                                                                        //TODO: preferably, this should be set via KUBECONFIG env var, which  See https://cloud.google.com/sdk/gcloud/reference/container/clusters/get-credentials
                                                                        pathToKubeConfig: String,
                                                                        clusterId: KubernetesClusterIdentifier,
                                                                        blocker: Blocker,
                                                                        blockerBound: Semaphore[F],
                                                                     //This is not used anywhere yet, there should be a custom kube one
                                                                        retryConfig: RetryConfig = RetryPredicates.standardRetryConfig
                                                                     ): Resource[F,KubernetesService[F]] =
    for {
      _ <- Resource.liftF(performGKEtoKubernetesCLIAuthHandoff(pathToCredential, clusterId))
      fileReader <- Resource.make(
        Async[F].delay(new FileReader(pathToKubeConfig))
      )(reader => Async[F].delay(reader.close()))
      apiClient <- Resource.liftF(Async[F].delay(ClientBuilder.kubeconfig(
        KubeConfig.loadKubeConfig(fileReader)
      ).build()))
      _ = Configuration.setDefaultApiClient(apiClient)
      kubernetesApi = new CoreV1Api()
  } yield new KubernetesInterpreter(kubernetesApi, blocker, blockerBound, retryConfig)

  //doesn't work for multi-cluster or multi-project configs
  //doesn't refresh every hour
  //use this to get the arg for this function credentials <- GoogleServiceHttpInterpreter.credentialResourceWithScope(pathToCredential)
  private def getKubernetesApiWithGoogleCreds[F[_] : Async](credentials: GoogleCredentials): F[CoreV1Api] = {
    for {
      _ <- Async[F].delay(KubeConfig.registerAuthenticator(new ReplacedGCPAuthenticator(credentials)))
      client = Config.defaultClient()
      _ <- Async[F].delay(Configuration.setDefaultApiClient(client))
      api = new CoreV1Api()
    } yield api
  }

  private def performGKEtoKubernetesCLIAuthHandoff[F[_] : Async](pathToCredential: String,  clusterId: KubernetesClusterIdentifier): F[Unit] =
    for {
      _ <- Async[F].delay("gcloud config set container/use_application_default_credentials true".!)
      _ <- Async[F].delay(s"gcloud auth activate-service-account --key-file=${pathToCredential}".!)
      //The below line is where google magically sets up the credentials to a GKE cluster in our local kubernetes config file for us, see https://cloud.google.com/sdk/gcloud/reference/container/clusters/get-credentials
      _ <- Async[F].delay(
        s"gcloud container clusters get-credentials --project ${clusterId.project.value} --region ${clusterId.location.value} ${clusterId.clusterName.value}".!
      )
      //This is necessary to do some setup that the Kubernetes client is expecting
      _ <- Async[F].delay("kubectl get secrets".!)
    } yield ()
}


