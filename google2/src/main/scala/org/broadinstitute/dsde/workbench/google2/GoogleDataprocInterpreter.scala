package org.broadinstitute.dsde.workbench.google2

import cats.{Parallel, Show}
import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._
import cats.mtl.Ask
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.StatusCode.Code
import com.google.cloud.compute.v1.Operation
import com.google.cloud.dataproc.v1.{RegionName => _, _}
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.FieldMask
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.DoneCheckableInstances.{clusterRunningCheckable, resizingDoneCheckable}
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.DataprocRole.Master
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.google2.GoogleDataprocInterpreter.{containsPreemptibles, getAllInstanceNames}

import scala.concurrent.duration._
import scala.collection.JavaConverters._

private[google2] class GoogleDataprocInterpreter[F[_]: StructuredLogger: Timer: Parallel: ContextShift](
  clusterControllerClient: ClusterControllerClient,
  googleComputeService: GoogleComputeService[F],
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
  )(implicit ev: Ask[F, TraceId]): F[CreateClusterResponse] = {
    val config: ClusterConfig = createClusterConfig
      .map(config =>
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

  /**
   * Strictly speaking, it is not possible to 'stop' a Dataproc cluster altogether.
   * Instead, we approximate by:
   *   1. removing pre-emptible instances (if any) by resizing the cluster, since they would not be possible to restart
   *   2. stopping underlying nodes individually
   */
  override def stopCluster(project: GoogleProject,
                           region: RegionName,
                           clusterName: DataprocClusterName,
                           metadata: Option[Map[String, String]]
  )(implicit
    ev: Ask[F, TraceId]
  ): F[List[Operation]] =
    for {
      clusterInstances <- getClusterInstances(project, region, clusterName)

      // First, remove preemptible instances (if any) and wait until the removal is done
      remainingClusterInstances <-
        if (containsPreemptibles(clusterInstances))
          resizeCluster(project, region, clusterName, numWorkers = None, numPreemptibles = Some(0)) >> streamFUntilDone(
            getClusterInstances(project, region, clusterName),
            15,
            6 seconds
          ).compile.lastOrError
        else F.pure(clusterInstances)

      // Then, wait until the cluster's status transitions back to RUNNING (from UPDATING)
      // Otherwise, stopping the remaining instances may cause the cluster to get in to ERROR status
      _ <- streamFUntilDone(getCluster(project, region, clusterName), 15, 3 seconds).compile.lastOrError

      // Then, stop each remaining instance individually
      operations <- remainingClusterInstances.toList.parFlatTraverse {
        case (DataprocRoleZonePreemptibility(role, zone, _), instances) =>
          instances.toList.parTraverse { instance =>
            (role match {
              case Master =>
                metadata.traverse { md =>
                  googleComputeService.addInstanceMetadata(
                    project,
                    zone,
                    instance,
                    md
                  )
                } >> googleComputeService.stopInstance(project, zone, instance)
              case _ =>
                googleComputeService.stopInstance(project, zone, instance)
            }).recoverWith {
              case _: com.google.api.gax.rpc.NotFoundException => F.pure(Operation.getDefaultInstance)
              case e                                           => F.raiseError[Operation](e)
            }
          }
      }
    } yield operations

  override def resizeCluster(project: GoogleProject,
                             region: RegionName,
                             clusterName: DataprocClusterName,
                             numWorkers: Option[Int],
                             numPreemptibles: Option[Int]
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Option[ClusterOperationMetadata]] = {
    val workerMask = "config.worker_config.num_instances"
    val preemptibleMask = "config.secondary_worker_config.num_instances"

    val configAndMask = (numWorkers, numPreemptibles) match {
      case (Some(nw), Some(np)) =>
        val mask = FieldMask.newBuilder
          .addAllPaths(List(workerMask, preemptibleMask).asJava)
        val config = ClusterConfig.newBuilder
          .setWorkerConfig(InstanceGroupConfig.newBuilder.setNumInstances(nw))
          .setSecondaryWorkerConfig(InstanceGroupConfig.newBuilder.setNumInstances(np))
        Some((config, mask))

      case (Some(nw), None) =>
        val mask = FieldMask.newBuilder
          .addPaths(workerMask)
        val config = ClusterConfig.newBuilder
          .setWorkerConfig(InstanceGroupConfig.newBuilder.setNumInstances(nw))
        Some((config, mask))

      case (None, Some(np)) =>
        val mask = FieldMask.newBuilder
          .addPaths(preemptibleMask)
        val config = ClusterConfig.newBuilder
          .setSecondaryWorkerConfig(InstanceGroupConfig.newBuilder.setNumInstances(np))
        Some((config, mask))

      case (None, None) =>
        None
    }

    val updateClusterRequest = configAndMask.map { case (config, mask) =>
      val cluster = Cluster
        .newBuilder()
        .setClusterName(clusterName.value)
        .setConfig(config)
        .build()

      UpdateClusterRequest
        .newBuilder()
        .setClusterName(clusterName.value)
        .setCluster(cluster)
        .setRegion(region.value)
        .setProjectId(project.value)
        .setUpdateMask(mask)
        .build()
    }

    val updateCluster = updateClusterRequest
      .traverse { request =>
        Async[F]
          .async[ClusterOperationMetadata] { cb =>
            ApiFutures.addCallback(
              clusterControllerClient.updateClusterAsync(request).getMetadata,
              callBack(cb),
              MoreExecutors.directExecutor()
            )
          }
      }
      .handleErrorWith {
        case _: com.google.api.gax.rpc.NotFoundException => F.pure(none[ClusterOperationMetadata])
        case e                                           => F.raiseError[Option[ClusterOperationMetadata]](e)
      }

    for {
      traceId <- ev.ask
      res <- withLogging(
        updateCluster,
        Some(traceId),
        s"com.google.cloud.dataproc.v1.ClusterControllerClient.updateClusterAsync(${updateClusterRequest})"
      )
    } yield res
  }

  override def deleteCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
    ev: Ask[F, TraceId]
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

  override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
    ev: Ask[F, TraceId]
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
          Show.show[Option[Cluster]](c => s"${c.map(_.getStatus.getState.toString).getOrElse("Not found")}")
        )
      }
  }

  override def getClusterInstances(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
    implicit ev: Ask[F, TraceId]
  ): F[Map[DataprocRoleZonePreemptibility, Set[InstanceName]]] =
    for {
      cluster <- getCluster(project, region, clusterName)
    } yield cluster.map(getAllInstanceNames).getOrElse(Map.empty)

  override def getClusterError(
    operationName: OperationName
  )(implicit ev: Ask[F, TraceId]): F[Option[ClusterError]] =
    for {
      error <- retryF(
        recoverF(
          Async[F].delay(clusterControllerClient.getOperationsClient.getOperation(operationName.value).getError),
          whenStatusCode(404)
        ),
        s"com.google.cloud.dataproc.v1.ClusterControllerClient.getOperationsClient.getOperation(${operationName.value}).getError()"
      )
    } yield error.map(e => ClusterError(e.getCode, e.getMessage))

  private def retryF[A](fa: F[A], loggingMsg: String)(implicit ev: Ask[F, TraceId]): F[A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(blocker.blockOn(fa)), loggingMsg).compile.lastOrError
}

object GoogleDataprocInterpreter {
  // WARNING: Be very careful refactoring this function and make sure you test this out in console.
  // Incorrectness in this function can cause leonardo fail to stop all instances for a Dataproc cluster, which
  // incurs compute cost for users
  def getAllInstanceNames(cluster: Cluster): Map[DataprocRoleZonePreemptibility, Set[InstanceName]] = {
    val res = Option(cluster.getConfig).map { config =>
      val zoneUri = config.getGceClusterConfig.getZoneUri
      val zone = ZoneName.fromUriString(zoneUri).getOrElse(ZoneName(""))

      val master =
        Option(config.getMasterConfig)
          .map(config => getFromGroup(DataprocRole.Master, config, zone))
          .getOrElse(Map.empty)
      val workers =
        Option(config.getWorkerConfig)
          .map(config => getFromGroup(DataprocRole.Worker, config, zone))
          .getOrElse(Map.empty)
      val secondaryWorkers = Option(config.getSecondaryWorkerConfig)
        .map(config => getFromGroup(DataprocRole.SecondaryWorker, config, zone))
        .getOrElse(Map.empty)

      master ++ workers ++ secondaryWorkers
    }

    res.getOrElse(Map.empty)
  }

  def containsPreemptibles(instances: Map[DataprocRoleZonePreemptibility, Set[InstanceName]]): Boolean =
    instances.exists { case (DataprocRoleZonePreemptibility(_, _, isPreemptible), instanceNames) =>
      isPreemptible && instanceNames.nonEmpty
    }

  private def getFromGroup(role: DataprocRole,
                           groupConfig: InstanceGroupConfig,
                           zone: ZoneName
  ): Map[DataprocRoleZonePreemptibility, Set[InstanceName]] = {
    val instances = groupConfig.getInstanceNamesList
      .asByteStringList()
      .asScala
      .toList
      .map(byteString => InstanceName(byteString.toStringUtf8))
      .toSet

    if (instances.isEmpty) Map.empty
    else Map(DataprocRoleZonePreemptibility(role, zone, groupConfig.getIsPreemptible) -> instances)
  }
}
