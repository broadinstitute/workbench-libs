package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Resource, Timer}
import cats.mtl.ApplicativeAsk
import com.google.container.v1.Cluster
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.cloud.container.v1.{ClusterManagerClient, ClusterManagerSettings}
import com.google.container.v1.Operation
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import cats.implicits._

trait GoogleKubernetesService[F[_]] {
  def createCluster(kubernetesClusterRequest: KubernetesCreateClusterRequest)
                   (implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def deleteCluster(clusterId: KubernetesClusterId)
                   (implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def getCluster(clusterId: KubernetesClusterId)
                (implicit ev: ApplicativeAsk[F, TraceId]): F[Cluster]
}

object GoogleKubernetesService {

  def resource[F[_] : StructuredLogger : Async : Timer : ContextShift](
                                                                        pathToCredential: String,
                                                                        blocker: Blocker,
                                                                        blockerBound: Semaphore[F],
                                                                        retryConfig: RetryConfig = RetryPredicates.retryConfigWithPredicates(whenStatusCode(404))
                                                                      ): Resource[F, GoogleKubernetesService[F]] = {
    for {
      credential <- credentialResource(pathToCredential)
      credentialsProvider = FixedCredentialsProvider.create(credential)
      clusterManagerSettings = ClusterManagerSettings.newBuilder()
        .setCredentialsProvider(credentialsProvider)
        .build()
      clusterManager <- Resource.make(Async[F].delay(
        ClusterManagerClient.create(clusterManagerSettings)
      ))(client =>  Async[F].delay(client.shutdown()) >> Async[F].delay(client.close()))
    } yield new GoogleKubernetesInterpreter[F](clusterManager, blocker, blockerBound, retryConfig)
  }

}
