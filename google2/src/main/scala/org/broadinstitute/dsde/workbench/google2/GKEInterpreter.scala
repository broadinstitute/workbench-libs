package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.ApplicativeAsk
import com.google.cloud.container.v1.ClusterManagerClient
import com.google.container.v1.{Cluster, CreateClusterRequest, GetOperationRequest, Operation}
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.GKEModels._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId

import scala.concurrent.duration.FiniteDuration

class GKEInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
  clusterManagerClient: ClusterManagerClient,
  blocker: Blocker,
  blockerBound: Semaphore[F],
  retryConfig: RetryConfig
)(implicit ev: ApplicativeAsk[F, TraceId])
    extends GKEService[F] {

  override def createCluster(
    kubernetesClusterRequest: KubernetesCreateClusterRequest
  ): F[Operation] = {
    val parent = Parent(kubernetesClusterRequest.project, kubernetesClusterRequest.location)

    val createClusterRequest: CreateClusterRequest = CreateClusterRequest
      .newBuilder()
      .setParent(parent.parentString)
      .setCluster(kubernetesClusterRequest.cluster)
      .build()

    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.createCluster(createClusterRequest)),
      f"com.google.cloud.container.v1.ClusterManagerClient.createCluster(${kubernetesClusterRequest})"
    )
  }

  override def getCluster(clusterId: KubernetesClusterId): F[Option[Cluster]] =
    tracedGoogleRetryWithBlocker(
      recoverF(
        Async[F].delay(clusterManagerClient.getCluster(clusterId.idString)),
        whenStatusCode(404)
      ),
      f"com.google.cloud.container.v1.ClusterManagerClient.getCluster(${clusterId.idString})"
    )

  override def deleteCluster(clusterId: KubernetesClusterId): F[Operation] =
    tracedGoogleRetryWithBlocker(
      Async[F].delay(clusterManagerClient.deleteCluster(clusterId.idString)),
      f"com.google.cloud.container.v1.ClusterManagerClient.deleteCluster(${clusterId.idString})"
    )

  //delete and create operations take around ~5mins with simple tests, could be longer for larger clusters
  override def pollOperation(operationId: KubernetesOperationId, delay: FiniteDuration, maxAttempts: Int): Stream[F, GKEPollOperation] = {
    val opString = operationId.idString

    val request = GetOperationRequest.newBuilder()
      .setName(operationId.idString)
      .build()

    val getOperation = Async[F].delay(clusterManagerClient.getOperation(request))

    streamFUntilDone(getOperation, GKEPollOperation.apply, maxAttempts, delay)
    //TODO: remove below before merge, just displaying the alternative
//    (Stream.eval(getOperation) ++ Stream.sleep_(delay))
//      .repeatN(maxAttempts)
//      .map(GKEPollOperation)
//      .takeThrough(!_.isDone)
  }

  private def tracedGoogleRetryWithBlocker[A](fa: F[A], action: String): F[A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(
                                      blocker.blockOn(fa)
                                    ),
                                    action).compile.lastOrError

}
