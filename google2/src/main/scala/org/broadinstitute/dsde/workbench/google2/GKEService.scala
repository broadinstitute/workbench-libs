package org.broadinstitute.dsde.workbench.google2

import java.nio.file.Path

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Resource, Sync, Timer}
import cats.mtl.ApplicativeAsk
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.container.v1.{Cluster, NodePool, Operation}
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.services.container.Container
import com.google.cloud.container.v1.{ClusterManagerClient, ClusterManagerSettings}
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.{DoneCheckable, RetryConfig}
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._

import scala.concurrent.duration.FiniteDuration

trait GKEService[F[_]] {

  // Note createCluster uses the legacy com.google.api.services.container client rather than
  // the newer com.google.container.v1 client because certain options like Workload Identity
  // are only available in the old client.
  def createCluster(request: KubernetesCreateClusterRequest)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[com.google.api.services.container.model.Operation]]

  def deleteCluster(clusterId: KubernetesClusterId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Operation]]

  def getCluster(clusterId: KubernetesClusterId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Cluster]]

  def createNodepool(request: KubernetesCreateNodepoolRequest)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[com.google.api.services.container.model.Operation]]

  def getNodepool(nodepoolId: NodepoolId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[NodePool]]

  def deleteNodepool(nodepoolId: NodepoolId)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Operation]]

  def pollOperation(operationId: KubernetesOperationId, delay: FiniteDuration, maxAttempts: Int)(
    implicit ev: ApplicativeAsk[F, TraceId],
    doneEv: DoneCheckable[Operation]
  ): Stream[F, Operation]
}

// The credentials passed to this object should have the permissions:
// Kubernetes Engine Admin
// Service account user

object GKEService {

  def resource[F[_]: StructuredLogger: Async: Timer: ContextShift](
    pathToCredential: Path,
    blocker: Blocker,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig = retryConfigWithPredicates(whenStatusCode(404), standardRetryPredicate, gkeRetryPredicate)
  ): Resource[F, GKEService[F]] =
    for {
      credential <- credentialResource(pathToCredential.toString)
      credentialsProvider = FixedCredentialsProvider.create(credential)
      clusterManagerSettings = ClusterManagerSettings
        .newBuilder()
        .setCredentialsProvider(credentialsProvider)
        .build()
      clusterManager <- backgroundResourceF(ClusterManagerClient.create(clusterManagerSettings))
      legacyClient <- legacyClient(pathToCredential)
    } yield new GKEInterpreter[F](clusterManager, legacyClient, blocker, blockerBound, retryConfig)

  private def legacyClient[F[_]: Sync](
    pathToCredential: Path
  ): Resource[F, com.google.api.services.container.Container] =
    for {
      httpTransport <- Resource.liftF(Sync[F].delay(GoogleNetHttpTransport.newTrustedTransport))
      jsonFactory = JacksonFactory.getDefaultInstance
      googleCredential <- legacyGoogleCredential(pathToCredential.toString)
      legacyClient = new Container.Builder(httpTransport, jsonFactory, googleCredential)
        .setApplicationName("workbench-libs")
        .build()
    } yield legacyClient

}
