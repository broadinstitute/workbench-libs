package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.StatusCode.Code
import com.google.cloud.dataproc.v1._
import com.google.common.util.concurrent.MoreExecutors
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.RetryConfig
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId

import scala.concurrent.duration._

private[google2] class GoogleDataprocInterpreter[F[_]: Async: Logger: Timer: ContextShift](clusterControllerClient: ClusterControllerClient,
                                                                             retryConfig: RetryConfig,
                                                                             blocker: Blocker,
                                                                             blockerBound: Semaphore[F]) extends GoogleDataproc[F] {

  override def createCluster(region: RegionName, clusterName: ClusterName, createClusterConfig: Option[CreateClusterConfig])
                            (implicit ev: ApplicativeAsk[F, TraceId]): F[CreateClusterResponse] = {
    val config: ClusterConfig = createClusterConfig.map(config => ClusterConfig
      .newBuilder
      .setGceClusterConfig(config.gceClusterConfig)
      .setInitializationActions(0, config.nodeInitializationAction)
      .setMasterConfig(config.instanceGroupConfig)
      .setConfigBucket(config.stagingBucket.value)
      .setSoftwareConfig(config.softwareConfig)
      .build()
    ).getOrElse(ClusterConfig.newBuilder.build())

    val cluster = Cluster
      .newBuilder()
      .setClusterName(clusterName.asString)
      .setConfig(config)
      .build()


    val request = CreateClusterRequest.newBuilder()
      .setCluster(cluster)
      .setRegion(region.getRegion)
      .setProjectId(region.getProject)
      .build()

    val createCluster = Async[F].async[ClusterOperationMetadata]{
      cb =>
        ApiFutures.addCallback(
          clusterControllerClient.createClusterAsync(request).getMetadata,
          callBack(cb),
          MoreExecutors.directExecutor()
        )
    }

    for {
      createCluster <- tracedRetryGoogleF(retryConfig)(
        createCluster,
        s"com.google.cloud.dataproc.v1.ClusterControllerClient.deleteClusterAsync(${region}, ${clusterName}, ${createClusterConfig})")
        .compile
        .lastOrError
        .attempt

      result <- createCluster match {
        case Left(e: com.google.api.gax.rpc.ApiException) =>
          if(e.getStatusCode.getCode == Code.ALREADY_EXISTS)
            Async[F].pure(CreateClusterResponse.AlreadyExists: CreateClusterResponse)
          else
            Async[F].raiseError(e): F[CreateClusterResponse]
        case Left(e) => Async[F].raiseError(e): F[CreateClusterResponse]
        case Right(v) => Async[F].pure(CreateClusterResponse.Success(v): CreateClusterResponse)
      }
    } yield result
  }

  override def deleteCluster(region: RegionName, clusterName: ClusterName)
                            (implicit ev: ApplicativeAsk[F, TraceId]): F[DeleteClusterResponse] = {
    val request = DeleteClusterRequest.newBuilder()
      .setRegion(region.getRegion)
      .setProjectId(region.getProject)
      .setClusterName(clusterName.asString)
      .build()

    val deleteCluster = Async[F].async[ClusterOperationMetadata]{
      cb =>
        ApiFutures.addCallback(
          clusterControllerClient.deleteClusterAsync(request).getMetadata,
          callBack(cb),
          MoreExecutors.directExecutor()
        )
    }

    for {
      createCluster <- tracedRetryGoogleF(retryConfig)(
        deleteCluster,
        "com.google.cloud.dataproc.v1.ClusterControllerClient.deleteClusterAsync(com.google.cloud.dataproc.v1.DeleteClusterRequest)")
        .compile
        .lastOrError
        .attempt

      result <- createCluster match {
        case Left(e: com.google.api.gax.rpc.ApiException) =>
          e.getStatusCode.getCode match {
            case Code.NOT_FOUND =>
              Async[F].pure(DeleteClusterResponse.NotFound: DeleteClusterResponse)
            case _ => Async[F].raiseError(e): F[DeleteClusterResponse]
          }
        case Left(e) => Async[F].raiseError(e): F[DeleteClusterResponse]
        case Right(v) => Async[F].pure(DeleteClusterResponse.Success(v): DeleteClusterResponse)
      }
    } yield result
  }

  override def getCluster(region: RegionName, clusterName: ClusterName)
                         (implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Cluster]] = {
    for {
      clusterAttempted <- blockingF(
        Async[F].delay(clusterControllerClient.getCluster(region.getProject(), region.getRegion(), clusterName.asString)),
        s"com.google.cloud.dataproc.v1.ClusterControllerClient.getCluster(${region.getProject()}, ${region.getRegion()}, ${clusterName})"
      ).compile.lastOrError.attempt

      resEither = clusterAttempted.map(c => Some(c)).leftFlatMap {
        case e: com.google.api.gax.rpc.ApiException =>
          if(e.getStatusCode.getCode == Code.NOT_FOUND)
            Right(None): Either[Throwable, Option[Cluster]]
          else
            Left(e)
        case e => Left(e)
      }
      result <- Async[F].fromEither(resEither)
    } yield result
  }

  private def blockingF[A](fa: F[A], loggingMsg: String)(implicit ev: ApplicativeAsk[F, TraceId]): Stream[F, A] =
    Stream.eval(ev.ask).flatMap(traceId => retryGoogleF(retryConfig)(blockerBound.withPermit(blocker.blockOn(fa)), Some(traceId), loggingMsg))
}

object GoogleDataprocInterpreter {
  val defaultRetryConfig = RetryConfig(
    org.broadinstitute.dsde.workbench.util.addJitter(1 seconds, 1 seconds),
    x => x * 2,
    5,
    {
      case e: com.google.api.gax.rpc.ApiException => e.isRetryable()
      case other => scala.util.control.NonFatal.apply(other)
    }
  )
}