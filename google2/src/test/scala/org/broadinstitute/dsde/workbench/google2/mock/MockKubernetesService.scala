package org.broadinstitute.dsde.workbench.google2
package mock

import cats.effect.IO
import cats.mtl.ApplicativeAsk
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterId
import org.broadinstitute.dsde.workbench.google2.KubernetesModels.{KubernetesNamespace, KubernetesPodStatus}
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName.{PodName, ServiceName}
import org.broadinstitute.dsde.workbench.model.{IP, TraceId}

class MockKubernetesService extends org.broadinstitute.dsde.workbench.google2.KubernetesService[IO] {
  override def createNamespace(
    clusterId: GKEModels.KubernetesClusterId,
    namespace: KubernetesModels.KubernetesNamespace
  )(implicit ev: ApplicativeAsk[IO, TraceId]): IO[Unit] = IO.unit

  override def deleteNamespace(
    clusterId: GKEModels.KubernetesClusterId,
    namespace: KubernetesModels.KubernetesNamespace
  )(implicit ev: ApplicativeAsk[IO, TraceId]): IO[Unit] = IO.unit

  override def createServiceAccount(
    clusterId: GKEModels.KubernetesClusterId,
    serviceAccount: KubernetesModels.KubernetesServiceAccount,
    namespaceName: KubernetesModels.KubernetesNamespace
  )(implicit ev: ApplicativeAsk[IO, TraceId]): IO[Unit] = IO.unit

  override def createPod(
    clusterId: GKEModels.KubernetesClusterId,
    pod: KubernetesModels.KubernetesPod,
    namespace: KubernetesModels.KubernetesNamespace
  )(implicit ev: ApplicativeAsk[IO, TraceId]): IO[Unit] = IO.unit

  override def listPodStatus(clusterId: GKEModels.KubernetesClusterId, namespace: KubernetesModels.KubernetesNamespace)(
    implicit ev: ApplicativeAsk[IO, TraceId]
  ): IO[List[KubernetesModels.KubernetesPodStatus]] =
    IO(List(KubernetesPodStatus.apply(PodName("test"), KubernetesModels.PodStatus.Running)))

  override def createService(
    clusterId: GKEModels.KubernetesClusterId,
    service: KubernetesModels.KubernetesServiceKind,
    namespace: KubernetesModels.KubernetesNamespace
  )(implicit ev: ApplicativeAsk[IO, TraceId]): IO[Unit] = IO.unit

  override def getServiceExternalIp(clusterId: KubernetesClusterId,
                                    namespace: KubernetesNamespace,
                                    serviceName: ServiceName)(
    implicit ev: ApplicativeAsk[IO, TraceId]
  ): IO[Option[IP]] = IO(Some(IP("1.2.3.4")))

  override def createRole(
    clusterId: GKEModels.KubernetesClusterId,
    role: KubernetesModels.KubernetesRole,
    namespace: KubernetesModels.KubernetesNamespace
  )(implicit ev: ApplicativeAsk[IO, TraceId]): IO[Unit] = IO.unit

  override def createRoleBinding(
    clusterId: GKEModels.KubernetesClusterId,
    roleBinding: KubernetesModels.KubernetesRoleBinding,
    namespace: KubernetesModels.KubernetesNamespace
  )(implicit ev: ApplicativeAsk[IO, TraceId]): IO[Unit] = IO.unit

  override def createSecret(
    clusterId: GKEModels.KubernetesClusterId,
    namespace: KubernetesModels.KubernetesNamespace,
    secret: KubernetesModels.KubernetesSecret
  )(implicit ev: ApplicativeAsk[IO, TraceId]): IO[Unit] = IO.unit
}

object MockKubernetesService extends MockKubernetesService
