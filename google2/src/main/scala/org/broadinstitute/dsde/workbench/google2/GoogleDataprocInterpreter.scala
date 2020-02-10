package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.StatusCode.Code
import com.google.cloud.dataproc.v1._
import com.google.common.util.concurrent.MoreExecutors
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

private[google2] class GoogleDataprocInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
                                                                                                      clusterControllerClient: ClusterControllerClient,
                                                                                                      retryConfig: RetryConfig,
                                                                                                      blocker: Blocker,
                                                                                                      blockerBound: Semaphore[F]
                                                                                                    ) extends GoogleDataprocService[F] {

  override def createCluster(
                              project: GoogleProject,
                              region: RegionName,
                              clusterName: ClusterName,
                              createClusterConfig: Option[CreateClusterConfig]
                            )(implicit ev: ApplicativeAsk[F, TraceId]): F[CreateClusterResponse] = {
    val config: ClusterConfig = createClusterConfig
      .map(
        config =>
          ClusterConfig.newBuilder
            .setGceClusterConfig(config.gceClusterConfig)
            .setInitializationActions(0, config.nodeInitializationAction)
            .setMasterConfig(config.instanceGroupConfig)
            .setConfigBucket(config.stagingBucket.value)
            .setSoftwareConfig(config.softwareConfig)
            .build()
      )
      .getOrElse(ClusterConfig.newBuilder.build())

    val cluster = Cluster
      .newBuilder()
      .setClusterName(clusterName.value)
      .setConfig(config)
      .build()

    val request = CreateClusterRequest
      .newBuilder()
      .setCluster(cluster)
      .setRegion(region.value)
      .setProjectId(project.value)
      .build()

    val createCluster = Async[F].async[ClusterOperationMetadata] { cb =>
      ApiFutures.addCallback(
        clusterControllerClient.createClusterAsync(request).getMetadata,
        callBack(cb),
        MoreExecutors.directExecutor()
      )
    }

    for {
      createCluster <- retryF(
        createCluster,
        s"com.google.cloud.dataproc.v1.ClusterControllerClient.createClusterAsync(${region}, ${clusterName}, ${createClusterConfig})"
      ).attempt

      result <- createCluster match {
        case Left(e: com.google.api.gax.rpc.ApiException) =>
          if (e.getStatusCode.getCode == Code.ALREADY_EXISTS)
            Async[F].pure(CreateClusterResponse.AlreadyExists: CreateClusterResponse)
          else
            Async[F].raiseError(e): F[CreateClusterResponse]
        case Left(e)  => Async[F].raiseError(e): F[CreateClusterResponse]
        case Right(v) => Async[F].pure(CreateClusterResponse.Success(v): CreateClusterResponse)
      }
    } yield result
  }

  override def deleteCluster(project: GoogleProject, region: RegionName, clusterName: ClusterName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[DeleteClusterResponse] = {
    val request = DeleteClusterRequest
      .newBuilder()
      .setRegion(region.value)
      .setProjectId(project.value)
      .setClusterName(clusterName.value)
      .build()

    val deleteCluster = Async[F].async[ClusterOperationMetadata] { cb =>
      ApiFutures.addCallback(
        clusterControllerClient.deleteClusterAsync(request).getMetadata,
        callBack(cb),
        MoreExecutors.directExecutor()
      )
    }

    for {
      createCluster <- retryF(
        deleteCluster,
        "com.google.cloud.dataproc.v1.ClusterControllerClient.deleteClusterAsync(com.google.cloud.dataproc.v1.DeleteClusterRequest)"
      ).attempt

      result <- createCluster match {
        case Left(e: com.google.api.gax.rpc.ApiException) =>
          e.getStatusCode.getCode match {
            case Code.NOT_FOUND =>
              Async[F].pure(DeleteClusterResponse.NotFound: DeleteClusterResponse)
            case _ => Async[F].raiseError(e): F[DeleteClusterResponse]
          }
        case Left(e)  => Async[F].raiseError(e): F[DeleteClusterResponse]
        case Right(v) => Async[F].pure(DeleteClusterResponse.Success(v): DeleteClusterResponse)
      }
    } yield result
  }

  override def getCluster(project: GoogleProject, region: RegionName, clusterName: ClusterName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Cluster]] =
    retryF(
      recoverF(Async[F].delay(clusterControllerClient.getCluster(project.value, region.value, clusterName.value)),
        whenStatusCode(404)),
      s"com.google.cloud.dataproc.v1.ClusterControllerClient.getCluster(${project.value}, ${region.value}, ${clusterName.value})"
    )

  private def retryF[A](fa: F[A], loggingMsg: String)(implicit ev: ApplicativeAsk[F, TraceId]): F[A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(blocker.blockOn(fa)), loggingMsg).compile.lastOrError
}