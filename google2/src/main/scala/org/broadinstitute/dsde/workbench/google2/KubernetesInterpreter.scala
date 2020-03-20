package org.broadinstitute.dsde.workbench.google2

import java.io.ByteArrayInputStream

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Resource, Timer}
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import com.google.container.v1.Cluster
import io.kubernetes.client.{ApiClient, ApiException}
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.util.Config
import org.broadinstitute.dsde.workbench.model.TraceId

import scala.collection.concurrent.TrieMap

// This uses a kubernetes client library to make calls to the kubernetes API. The client library is autogenerated from the kubernetes API.
// It is highly recommended to use the kubernetes API docs here https://kubernetes.io/docs/reference/generated/kubernetes-api as opposed to the client library docs

class KubernetesInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
                                                                                 credentials: GoogleCredentials,
                                                                                 googleKubernetesService: GoogleKubernetesService[F],
                                                                                 blocker: Blocker,
                                                                                 blockerBound: Semaphore[F],
                                                                                 retryConfig: RetryConfig
                                                                               ) extends KubernetesService[F] {
  //here, we store the apiClient so we do not have to make the google calls necessary to construct it more often than needed
  private val clientStore = TrieMap[KubernetesClusterId,ApiClient]()

  // https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#podspec-v1-core
  override def createPod(clusterId: KubernetesClusterId, pod: KubernetesPod, namespace: KubernetesNamespace)
                        (implicit ev: ApplicativeAsk[F, TraceId]): F[Unit] =
    blockingClientProvider(clusterId, { kubernetesClient =>
      Async[F].delay(kubernetesClient.createNamespacedPod(namespace.name.value, pod.getJavaSerialization, null, null, null))
    })


  //why we use a service over a deployment https://matthewpalmer.net/kubernetes-app-developer/articles/service-kubernetes-example-tutorial.html
  //services can be applied to pods/containers, while deployments are for pre-creating pods/containers
  override def createService(clusterId: KubernetesClusterId, service: KubernetesServiceKind, namespace: KubernetesNamespace)
                   (implicit ev: ApplicativeAsk[F, TraceId]): F[Unit] =
    blockingClientProvider(clusterId, { kubernetesClient =>
       Async[F].delay(kubernetesClient.createNamespacedService(namespace.name.value, service.getJavaSerialization, null, null, null))
    })

  override def createNamespace(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)
                              (implicit ev: ApplicativeAsk[F, TraceId]): F[Unit] =
    blockingClientProvider(clusterId, { kubernetesClient =>
      Async[F].delay(kubernetesClient.createNamespace(namespace.getJavaSerialization, null, null, null))
    })

  private def getClient(clusterId: KubernetesClusterId)
                       (implicit ev: ApplicativeAsk[F, TraceId]): F[CoreV1Api] = {
    val clientOpt = clientStore.get(clusterId)

    //we always update the token, even for existing clients, so we don't have to maintain a reference to the last time each client was updated
    //unfortunately, the kubernetes client does not implement a gcp authenticator, so we must do this ourselves.
    //See this for details https://github.com/kubernetes-client/java/issues/290
    credentials.refreshIfExpired()
    val token = credentials.getAccessToken

    clientOpt match {
      case Some(client) => Async[F].delay(client.setApiKey(token.getTokenValue)) >>
        Async[F].delay(new CoreV1Api(client))
      case None => for {
        cluster <- googleKubernetesService.getCluster(clusterId)
        client <- createClient(cluster, token)
        _ = clientStore.put(clusterId, client)
      } yield new CoreV1Api(client)
    }
  }

  //The underlying http client for ApiClient claims that it releases idle threads and that shutdown is not necessary
  //Here is a guide on how to proactively release resource if this proves to be problematic https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/#shutdown-isnt-necessary
  private def createClient(cluster: Cluster, token: AccessToken): F[ApiClient] = {
    val endpoint = KubernetesMasterIP(cluster.getEndpoint)
    val cert = KubernetesClusterCaCert(cluster.getMasterAuth.getClusterCaCertificate)
    val certResource = Resource.make(
      Async[F].delay(new ByteArrayInputStream(cert.base64Cert))
    )(stream => Async[F].delay(stream.close()))

    certResource.use { certStream =>
      Async[F].delay(Config.fromToken(
        endpoint.url,
        token.getTokenValue
      ).setSslCaCert(certStream))
    }
  }

  //TODO: retry once we know what kubernetes codes are applicable
  private def blockingClientProvider[A](clusterId: KubernetesClusterId, fa: CoreV1Api => F[A])
                                      (implicit ev: ApplicativeAsk[F, TraceId]): F[A] =
    blockerBound.withPermit(blocker.blockOn(
      for {
        kubernetesClient <- getClient(clusterId)
      } yield fa(kubernetesClient)
    ).onError { //we aren't handling it here, it will be bubbled up, but we want to print a more helpful message
      case e: ApiException => Async[F].delay(println(e.getResponseBody()))
    })

 }
