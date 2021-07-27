package org.broadinstitute.dsde.workbench.google2

import cats.effect.std.{Dispatcher, Semaphore}
import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.google.api.services.container.ContainerScopes
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterId
import org.broadinstitute.dsde.workbench.google2.KubernetesModels._
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName.ServiceName
import org.broadinstitute.dsde.workbench.model.{IP, TraceId}
import org.typelevel.log4cats.StructuredLogger

import java.nio.file.Path
import scala.collection.JavaConverters._

trait KubernetesService[F[_]] {
  // These methods do not fail on 409 in the case of create and 404 in the case of delete
  // This ensures idempotency (i.e., you can repeatedly call create/delete for the same resource without error)

  // namespaces group resources, and allow our list/get/update API calls to be segmented. This can be used on a per-user basis, for example
  def createNamespace(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  def deleteNamespace(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  def namespaceExists(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Boolean]

  def deletePv(clusterId: KubernetesClusterId, pv: PvName)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  // A Kubernetes service account is an automatically enabled authenticator that uses signed bearer tokens to verify requests.
  // NB: It is distinct from Google service accounts.
  def createServiceAccount(clusterId: KubernetesClusterId,
                           serviceAccount: KubernetesServiceAccount,
                           namespaceName: KubernetesNamespace
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  // pods represent a set of containers
  def createPod(clusterId: KubernetesClusterId, pod: KubernetesPod, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  def listPodStatus(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[List[KubernetesPodStatus]]

  // certain services allow us to expose various containers via a matching selector
  def createService(clusterId: KubernetesClusterId, service: KubernetesServiceKind, namespace: KubernetesNamespace)(
    implicit ev: Ask[F, TraceId]
  ): F[Unit]

  def getServiceExternalIp(clusterId: KubernetesClusterId, namespace: KubernetesNamespace, serviceName: ServiceName)(
    implicit ev: Ask[F, TraceId]
  ): F[Option[IP]]

  def listPersistentVolumeClaims(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[List[V1PersistentVolumeClaim]]

  def createRole(clusterId: KubernetesClusterId, role: KubernetesRole, namespace: KubernetesNamespace)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  def createRoleBinding(clusterId: KubernetesClusterId,
                        roleBinding: KubernetesRoleBinding,
                        namespace: KubernetesNamespace
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]

  def createSecret(clusterId: KubernetesClusterId, namespace: KubernetesNamespace, secret: KubernetesSecret)(implicit
    ev: Ask[F, TraceId]
  ): F[Unit]
}

// This kubernetes service requires a GKEService because it needs to call getCluster
// This is needed because an instance of the underlying client lib for each cluster is stored as state to reduce the number google calls needed by consumers

// The credentials passed to this object should have the permissions:
// Kubernetes Engine Admin
// Service account user

object KubernetesService {
  def resource[F[_]: StructuredLogger: Async](
    pathToCredential: Path,
    gkeService: GKEService[F]
  ): Resource[F, KubernetesService[F]] =
    for {
      credentials <- credentialResource(pathToCredential.toString)
      scopedCredential = credentials.createScoped(Seq(ContainerScopes.CLOUD_PLATFORM).asJava)
    } yield new KubernetesInterpreter(scopedCredential, gkeService)
}
