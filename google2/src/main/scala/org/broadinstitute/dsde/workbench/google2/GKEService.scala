package org.broadinstitute.dsde.workbench.google2

import java.nio.file.Path

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Resource, Timer}
import cats.mtl.ApplicativeAsk
import com.google.container.v1.Cluster
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.cloud.container.v1.{ClusterManagerClient, ClusterManagerSettings}
import com.google.container.v1.Operation
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._

trait GKEService[F[_]] {
  def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest): F[Operation]

  def deleteCluster(clusterId: KubernetesClusterId): F[Operation]

  def getCluster(clusterId: KubernetesClusterId): F[Option[Cluster]]
}

// The credentials passed to this object should have the permissions:
// Kubernetes Engine Admin
// Service account user

object GKEService {

  def resource[F[_]: StructuredLogger: Async: Timer: ContextShift](
                                                                    pathToCredential: Path,
                                                                    blocker: Blocker,
                                                                    blockerBound: Semaphore[F],
                                                                    retryConfig: RetryConfig = RetryPredicates.retryConfigWithPredicates(whenStatusCode(404))
  )(implicit ev: ApplicativeAsk[F, TraceId]): Resource[F, GKEService[F]] =
    for {
      credential <- credentialResource(pathToCredential.toString)
      credentialsProvider = FixedCredentialsProvider.create(credential)
      clusterManagerSettings = ClusterManagerSettings
        .newBuilder()
        .setCredentialsProvider(credentialsProvider)
        .build()
      clusterManager <- backgroundResourceF(ClusterManagerClient.create(clusterManagerSettings))
    } yield new GKEInterpreter[F](clusterManager, blocker, blockerBound, retryConfig)

}
