package org.broadinstitute.dsde.workbench.google2

import java.nio.file.Path

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Effect, Resource, Timer}

import scala.collection.JavaConverters._
import com.google.api.services.container.ContainerScopes
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterId
import org.broadinstitute.dsde.workbench.google2.KubernetesClientModels._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates

trait KubernetesService[F[_]] {
  //namespaces group resources, and allow our list/get/update API calls to be segmented. This can be used on a per-user basis, for example
  def createNamespace(clusterId: KubernetesClusterId, namespace: KubernetesNamespace): F[Unit]

  //pods represent a set of containers
  def createPod(clusterId: KubernetesClusterId, pod: KubernetesPod, namespace: KubernetesNamespace): F[Unit]

  //certain services allow us to expose various containers via a matching selector
  def createService(clusterId: KubernetesClusterId, service: KubernetesServiceKind, namespace: KubernetesNamespace): F[Unit]
}

// This kubernetes service requires a GKEService because it needs to call getCluster
// This is needed because an instance of the underlying client lib for each cluster is stored as state to reduce the number google calls needed by consumers

// The credentials passed to this object should have the permissions:
// Kubernetes Engine Admin
// Service account user

object KubernetesService {
  def resource[F[_]: StructuredLogger: Async: Effect: Timer: ContextShift](
                                                                    pathToCredential: Path,
                                                                    gkeService: GKEService[F],
                                                                    blocker: Blocker,
                                                                    blockerBound: Semaphore[F],
                                                                    //This is not used anywhere yet, there should be a custom kube one
                                                                    retryConfig: RetryConfig = RetryPredicates.standardRetryConfig
  ): Resource[F, KubernetesService[F]] =
    for {
      credentials <- credentialResource(pathToCredential.toString)
      scopedCredential = credentials.createScoped(Seq(ContainerScopes.CLOUD_PLATFORM).asJava)
    } yield new KubernetesInterpreter(scopedCredential, gkeService, blocker, blockerBound, retryConfig)
}
