package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore

import cats.effect.{Async, Blocker, ContextShift, Resource, Timer}
import cats.mtl.ApplicativeAsk

import scala.collection.JavaConverters._
import com.google.api.services.container.ContainerScopes
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.TraceId

trait KubernetesService[F[_]] {
  //namespaces group resources, and allow our list/get/update API calls to be segmented. This can be used on a per-user basis, for example
  def createNamespace(clusterId: KubernetesClusterId, namespace: KubernetesNamespace)
  (implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]

  //pods represent a set of containers
  def createPod(clusterId: KubernetesClusterId, pod: KubernetesPod, namespace: KubernetesNamespace)
               (implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]

  //certain services allow us to expose various containers via a matching selector
  def createService(clusterId: KubernetesClusterId, service: KubernetesServiceKind, namespace: KubernetesNamespace)
                   (implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]
}


object KubernetesService {
  def resource[F[_]: StructuredLogger : Async : Timer : ContextShift](
                                                                     //the credential here needs Kubernetes Engine Admin and Service account user
                                                                        pathToCredential: String,
                                                                        googleKubernetesService: GoogleKubernetesService[F],
                                                                        blocker: Blocker,
                                                                        blockerBound: Semaphore[F],
                                                                     //This is not used anywhere yet, there should be a custom kube one
                                                                        retryConfig: RetryConfig = RetryPredicates.standardRetryConfig
                                                                     ): Resource[F,KubernetesService[F]] =
    for {
      credentials <- credentialResource(pathToCredential)
      scopedCredential = credentials.createScoped(Seq(ContainerScopes.CLOUD_PLATFORM).asJava)
    } yield new KubernetesInterpreter(scopedCredential, googleKubernetesService, blocker, blockerBound, retryConfig)
}


