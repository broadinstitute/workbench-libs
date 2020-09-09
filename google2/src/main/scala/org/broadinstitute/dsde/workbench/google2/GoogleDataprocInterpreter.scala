package org.broadinstitute.dsde.workbench.google2

import cats.Show
import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.StatusCode.Code
import com.google.cloud.dataproc.v1.{RegionName => _, _}
import com.google.common.util.concurrent.MoreExecutors
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.google2.GoogleDataprocInterpreter._

import scala.collection.JavaConverters._

private[google2] class GoogleDataprocInterpreter[F[_]: StructuredLogger: Timer: ContextShift](
  clusterControllerClient: ClusterControllerClient,
  blocker: Blocker,
  blockerBound: Semaphore[F],
  retryConfig: RetryConfig
)(implicit F: Async[F])
    extends GoogleDataprocService[F] {

  override def createCluster(
    project: GoogleProject,
    region: RegionName,
    clusterName: DataprocClusterName,
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

  override def deleteCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[ClusterOperationMetadata]] = {
    val request = DeleteClusterRequest
      .newBuilder()
      .setRegion(region.value)
      .setProjectId(project.value)
      .setClusterName(clusterName.value)
      .build()

    val deleteCluster = Async[F]
      .async[ClusterOperationMetadata] { cb =>
        ApiFutures.addCallback(
          clusterControllerClient.deleteClusterAsync(request).getMetadata,
          callBack(cb),
          MoreExecutors.directExecutor()
        )
      }
      .map(Option(_))
      .handleErrorWith {
        case _: com.google.api.gax.rpc.NotFoundException => F.pure(none[ClusterOperationMetadata])
        case e                                           => F.raiseError[Option[ClusterOperationMetadata]](e)
      }

    for {
      traceId <- ev.ask
      res <- withLogging(
        deleteCluster,
        Some(traceId),
        s"com.google.cloud.dataproc.v1.ClusterControllerClient.deleteClusterAsync(${request})"
      )
    } yield res
  }

  override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Cluster]] = {
    val fa =
      F.delay(clusterControllerClient.getCluster(project.value, region.value, clusterName.value))
        .map(Option(_))
        .handleErrorWith {
          case _: com.google.api.gax.rpc.NotFoundException => F.pure(none[Cluster])
          case e                                           => F.raiseError[Option[Cluster]](e)
        }

    ev.ask
      .flatMap { traceId =>
        withLogging(
          fa,
          Some(traceId),
          s"com.google.cloud.dataproc.v1.ClusterControllerClient.getCluster(${project.value}, ${region.value}, ${clusterName.value})",
          Show.show[Option[Cluster]](c => s"${c.map(_.getStatus.toString).getOrElse("Not found")}")
        )
      }
  }

  override def getClusterInstances(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Map[DataprocRole, Set[InstanceName]]] =
    for {
      cluster <- getCluster(project, region, clusterName)
    } yield cluster.map(c => getAllInstanceNames(c)).getOrElse(Map.empty)

  override def getClusterError(
    operationName: OperationName
  )(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[ClusterError]] =
    for {
      error <- retryF(
        recoverF(
          Async[F].delay(clusterControllerClient.getOperationsClient().getOperation(operationName.value).getError()),
          whenStatusCode(404)
        ),
        s"com.google.cloud.dataproc.v1.ClusterControllerClient.getOperationsClient.getOperation(${operationName.value}).getError()"
      )
    } yield error.map(e => ClusterError(e.getCode, e.getMessage))

  private def retryF[A](fa: F[A], loggingMsg: String)(implicit ev: ApplicativeAsk[F, TraceId]): F[A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(blocker.blockOn(fa)), loggingMsg).compile.lastOrError
}

object GoogleDataprocInterpreter {
  // WARNING: Be very careful refactoring this function and make sure you test this out in console.
  // Incorrectness in this function can cause leonardo fail to stop all instances for a Dataproc cluster, which
  // incurs compute cost for users
  def getAllInstanceNames(cluster: Cluster): Map[DataprocRole, Set[InstanceName]] = {
    def getFromGroup(role: DataprocRole, group: InstanceGroupConfig): Map[DataprocRole, Set[InstanceName]] = {
      val instances = group.getInstanceNamesList
        .asByteStringList()
        .asScala
        .toList
        .map(byteString => InstanceName(byteString.toStringUtf8))
        .toSet

      if (instances.isEmpty) Map.empty else Map(role -> instances)
    }

    val res = Option(cluster.getConfig).map { config =>
      val master =
        Option(config.getMasterConfig).map(config => getFromGroup(DataprocRole.Master, config)).getOrElse(Map.empty)
      val workers =
        Option(config.getWorkerConfig).map(config => getFromGroup(DataprocRole.Worker, config)).getOrElse(Map.empty)
      val secondaryWorkers = Option(config.getSecondaryWorkerConfig)
        .map(config => getFromGroup(DataprocRole.SecondaryWorker, config))
        .getOrElse(Map.empty)

      master ++ workers ++ secondaryWorkers
    }

    res.getOrElse(Map.empty)
  }
}
