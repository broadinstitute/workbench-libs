package org.broadinstitute.dsde.workbench.google2

import cats.Show
import cats.effect.Async
import cats.mtl.Ask
import cats.syntax.all._
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import com.google.container.v1.Cluster
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.{CoreV1Api, RbacAuthorizationV1Api}
import io.kubernetes.client.openapi.models.{V1NamespaceList, V1PersistentVolumeClaim}
import io.kubernetes.client.util.Config
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterId
import org.broadinstitute.dsde.workbench.google2.JavaSerializableInstances._
import org.broadinstitute.dsde.workbench.google2.JavaSerializableSyntax._
import org.broadinstitute.dsde.workbench.google2.KubernetesModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName.{PodName, ServiceName}
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.{IP, TraceId}
import org.broadinstitute.dsde.workbench.util2.withLogging
import org.typelevel.log4cats.StructuredLogger
import scalacache.Cache

import java.io.ByteArrayInputStream
import java.util.UUID
import scala.jdk.CollectionConverters._

// This uses a kubernetes client library to make calls to the kubernetes API. The client library is autogenerated from the kubernetes API.
// It is highly recommended to use the kubernetes API docs here https://kubernetes.io/docs/reference/generated/kubernetes-api as opposed to the client library docs

class KubernetesInterpreter[F[_]](
  credentials: GoogleCredentials,
  gkeService: GKEService[F],
  apiClientCache: Cache[F, KubernetesClusterId, ApiClient]
)(implicit F: Async[F], logger: StructuredLogger[F])
    extends KubernetesService[F] {
  private def getNewApiClient(clusterId: KubernetesClusterId): F[ApiClient] = {
    // we do not want to have to specify this at resource (class) creation time, so we create one on each load here
    implicit val traceId = Ask.const[F, TraceId](TraceId(UUID.randomUUID()))
    for {
      _ <- logger
        .info(s"Determined that there is no cached client for kubernetes cluster $clusterId. Creating a client")
      clusterOpt <- gkeService.getCluster(clusterId)
      cluster <- F.fromEither(
        clusterOpt.toRight(
          KubernetesClusterNotFoundException(
            s"Could not create client for cluster $clusterId because it does not exist in google"
          )
        )
      )
      token <- getToken
      client <- createClient(
        cluster,
        token
      )
    } yield client
  }

  // https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#podspec-v1-core
  override def createPod(clusterId: KubernetesClusterId, pod: KubernetesPod, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        recoverF(
          F.blocking(
            client.createNamespacedPod(namespace.name.value, pod.getJavaSerialization, "true", null, null, null)
          ),
          whenStatusCode(409)
        )
      _ <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.createNamespacedPod(${namespace.name.value}, ${pod.name.value}, true, null, null)"
      )
    } yield ()

  override def listPodStatus(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[List[KubernetesPodStatus]] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        F.blocking(
          client.listNamespacedPod(namespace.name.value,
                                   "true",
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null
          )
        )

      response <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.listNamespacedPod(${namespace.name.value}, true, null, null, null, null, null, null, null, null)"
      )

      listPodStatus = Option(response.getItems)
        .map { l =>
          l.asScala.toList.foldMap(v1Pod =>
            PodStatus.stringToPodStatus
              .get(v1Pod.getStatus.getPhase)
              .map(s => List(KubernetesPodStatus(PodName(v1Pod.getMetadata.getName), s)))
              .toRight(new RuntimeException(s"Unknown Google status ${v1Pod.getStatus.getPhase}"))
          )
        }
        .getOrElse(Nil.asRight[RuntimeException])

      res <- F.fromEither(listPodStatus)
    } yield res

  // Why we use a service over a deployment: https://matthewpalmer.net/kubernetes-app-developer/articles/service-kubernetes-example-tutorial.html
  // Services can be applied to pods/containers, while deployments are for pre-creating pods/containers.
  override def createService(clusterId: KubernetesClusterId,
                             service: KubernetesServiceKind,
                             namespace: KubernetesNamespace
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        recoverF(
          F.blocking(
            client.createNamespacedService(namespace.name.value, service.getJavaSerialization, "true", null, null, null)
          ),
          whenStatusCode(409)
        )
      _ <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.createNamespacedService(${namespace.name.value}, ${service.serviceName.value}, true, null, null)"
      )
    } yield ()

  override def getServiceExternalIp(clusterId: KubernetesClusterId,
                                    namespace: KubernetesNamespace,
                                    serviceName: ServiceName
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Option[IP]] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        F.blocking(
          client
            .listNamespacedService(namespace.name.value,
                                   "true",
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null
            )
        ).map(Option(_))
          .handleErrorWith {
            case e: io.kubernetes.client.openapi.ApiException if e.getCode == 404 => F.pure(None)
            case e: Throwable                                                     => F.raiseError(e)
          }
      responseOpt <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.listNamespacedService(${namespace.name.value}, true, null, null, null, null, null, null, null, null, null)"
      )

      // Many of these fields can be null, so null-check everything
      ipOpt = for {
        response <- responseOpt
        items <- Option(response.getItems)
        item <- items.asScala.filter(_.getMetadata.getName == serviceName.value).headOption
        status <- Option(item.getStatus)
        lb <- Option(status.getLoadBalancer)
        ingresses <- Option(lb.getIngress)
        ingress <- ingresses.asScala.headOption
        ip <- Option(ingress.getIp)
        res = IP(ip)
      } yield res

    } yield ipOpt

  override def listPersistentVolumeClaims(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[List[V1PersistentVolumeClaim]] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        F.blocking(
          client.listNamespacedPersistentVolumeClaim(namespace.name.value,
                                                     "true",
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null
          )
        ).map(Option(_))
          .handleErrorWith {
            case e: io.kubernetes.client.openapi.ApiException if e.getCode == 404 => F.pure(None)
            case e: Throwable                                                     => F.raiseError(e)
          }
      responseOpt <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.listNamespacedPersistentVolumeClaim(${namespace.name.value}, true, null, null, null, null, null, null, null, null)"
      )
    } yield responseOpt.map(_.getItems.asScala.toList).getOrElse(List.empty[V1PersistentVolumeClaim])

  override def deletePv(clusterId: KubernetesClusterId, pv: PvName)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        F.blocking(
          client.deletePersistentVolume(pv.asString, "true", null, null, null, null, null)
        ).map(Option(_))
          .handleErrorWith {
            case e: io.kubernetes.client.openapi.ApiException if e.getCode == 404 => F.pure(None)
            case e: Throwable                                                     => F.raiseError(e)
          }
      _ <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.deletePersistentVolume(${pv.asString}, true, null, null, null, null, null)"
      )
    } yield ()

  override def createNamespace(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        recoverF(
          F.blocking(
            client.createNamespace(namespace.getJavaSerialization, "true", null, null, null)
          ),
          whenStatusCode(409)
        )
      _ <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.createNamespace(${namespace.getJavaSerialization}, true, null, null)"
      )
    } yield ()

  override def namespaceExists(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Boolean] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        recoverF(
          F.blocking(
            client.listNamespace("true", false, null, null, null, null, null, null, null, null, false)
          ),
          whenStatusCode(409)
        )
      v1NamespaceList <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.listNamespace()",
        Show.show[Option[V1NamespaceList]](
          _.fold("No namespace found")(x => x.getItems.asScala.toList.map(_.getMetadata.getName).mkString(","))
        )
      )
    } yield v1NamespaceList
      .map(ls => ls.getItems.asScala.toList)
      .getOrElse(List.empty)
      .exists(x => x.getMetadata.getName == namespace.name.value)

  override def deleteNamespace(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] = {
    val delete = for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        recoverF(
          F.blocking(
            client.deleteNamespace(
              namespace.name.value,
              "true",
              null,
              null,
              null,
              null,
              null
            )
          ).void
            .recoverWith {
              case e: com.google.gson.JsonSyntaxException
                  if e.getMessage.contains("Expected a string but was BEGIN_OBJECT") =>
                logger.error(e)("Ignore response parsing error")
            } // see https://github.com/kubernetes-client/java/wiki/6.-Known-Issues#1-exception-on-deleting-resources-javalangillegalstateexception-expected-a-string-but-was-begin_object
          ,
          whenStatusCode(404)
        )
      _ <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.openapi.apis.CoreV1Api.deleteNamespace(${namespace.name.value}, true, null, null, null, null, null)"
      )
    } yield ()

    // There is a known bug with the client lib json decoding.  `com.google.gson.JsonSyntaxException` occurs every time.
    // See https://github.com/kubernetes-client/java/issues/86
    delete.handleErrorWith {
      case _: com.google.gson.JsonSyntaxException =>
        F.unit
      case e: Throwable => F.raiseError(e)
    }
  }

  override def createSecret(clusterId: KubernetesClusterId, namespace: KubernetesNamespace, secret: KubernetesSecret)(
    implicit ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        recoverF(
          F.blocking(
            client.createNamespacedSecret(namespace.name.value, secret.getJavaSerialization, "true", null, null, null)
          ),
          whenStatusCode(409)
        )
      _ <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.createNamespacedSecret(${namespace.name.value}, ${secret.name.value}, true, null, null)"
      )
    } yield ()

  override def createServiceAccount(clusterId: KubernetesClusterId,
                                    serviceAccount: KubernetesServiceAccount,
                                    namespace: KubernetesNamespace
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new CoreV1Api(_))
      call =
        recoverF(F.blocking(
                   client.createNamespacedServiceAccount(namespace.name.value,
                                                         serviceAccount.getJavaSerialization,
                                                         "true",
                                                         null,
                                                         null,
                                                         null
                   )
                 ),
                 whenStatusCode(409)
        )
      _ <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.createNamespacedServiceAccount(${namespace.name.value}, ${serviceAccount.name.value}, true, null, null)"
      )
    } yield ()

  override def createRole(clusterId: KubernetesClusterId, role: KubernetesRole, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new RbacAuthorizationV1Api(_))
      call =
        recoverF(
          F.blocking(
            client.createNamespacedRole(namespace.name.value, role.getJavaSerialization, "true", null, null, null)
          ),
          whenStatusCode(409)
        )
      _ <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.RbacAuthorizationV1Api.createNamespacedRole(${namespace.name.value}, ${role.name.value}, true, null, null)"
      )
    } yield ()

  override def createRoleBinding(clusterId: KubernetesClusterId,
                                 roleBinding: KubernetesRoleBinding,
                                 namespace: KubernetesNamespace
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] =
    for {
      traceId <- ev.ask
      client <- getClient(clusterId, new RbacAuthorizationV1Api(_))
      call =
        recoverF(F.blocking(
                   client.createNamespacedRoleBinding(namespace.name.value,
                                                      roleBinding.getJavaSerialization,
                                                      "true",
                                                      null,
                                                      null,
                                                      null
                   )
                 ),
                 whenStatusCode(409)
        )
      _ <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.RbacAuthorizationV1Api.createNamespacedRoleBinding(${namespace.name.value}, ${roleBinding.name.value}, true, null, null)"
      )
    } yield ()

  // DO NOT QUERY THE CACHE DIRECTLY
  // There is a wrapper method that is necessary to ensure the token is refreshed
  // we never make the entry stale, because we always need to refresh the token (see comment above getToken)
  // if we did stale the entry we would have to unnecessarily re-do the google call
  private def getClient[A](clusterId: KubernetesClusterId, fa: ApiClient => A): F[A] =
    for {
      client <- apiClientCache.cachingF(clusterId)(None)(getNewApiClient(clusterId))
      token <- getToken()
      _ <- F.blocking(client.setApiKey(token.getTokenValue))
    } yield fa(client)

  // we always update the token, even for existing clients, so we don't have to maintain a reference to the last time each client was updated
  // unfortunately, the kubernetes client does not implement a gcp authenticator, so we must do this ourselves.
  // See this for details https://github.com/kubernetes-client/java/issues/290
  private def getToken(): F[AccessToken] =
    for {
      _ <- F.blocking(credentials.refreshIfExpired())
    } yield credentials.getAccessToken

  // The underlying http client for ApiClient claims that it releases idle threads and that shutdown is not necessary
  // Here is a guide on how to proactively release resource if this proves to be problematic https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/#shutdown-isnt-necessary
  private def createClient(cluster: Cluster, token: AccessToken): F[ApiClient] = {
    val endpoint = KubernetesApiServerIp(cluster.getEndpoint)
    val cert = KubernetesClusterCaCert(cluster.getMasterAuth.getClusterCaCertificate)

    for {
      cert <- F.fromEither(cert.base64Cert)
      certResource = autoClosableResourceF(new ByteArrayInputStream(cert))
      apiClient <- certResource.use { certStream =>
        F.delay(
          Config
            .fromToken(
              endpoint.url,
              token.getTokenValue
            )
            .setSslCaCert(certStream)
        )
      }
    } yield apiClient // appending here a .setDebugging(true) prints out useful API request/response info for development
  }
}
