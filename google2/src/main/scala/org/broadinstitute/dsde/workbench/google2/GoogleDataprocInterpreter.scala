package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.std.Semaphore
import cats.mtl.Ask
import cats.syntax.all._
import cats.{Parallel, Show}
import com.google.api.core.ApiFutures
import com.google.cloud.compute.v1.Operation
import com.google.cloud.dataproc.v1.{RegionName => _, _}
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.FieldMask
import org.broadinstitute.dsde.workbench.google2.DataprocRole.Master
import org.broadinstitute.dsde.workbench.google2.GoogleDataprocInterpreter._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.{DoneCheckable, RetryConfig}
import org.typelevel.log4cats.StructuredLogger

import scala.collection.JavaConverters._
import scala.concurrent.duration._

private[google2] class GoogleDataprocInterpreter[F[_]: StructuredLogger: Parallel](
  clusterControllerClients: Map[RegionName, ClusterControllerClient],
  googleComputeService: GoogleComputeService[F],
  blockerBound: Semaphore[F],
  retryConfig: RetryConfig
)(implicit F: Async[F])
    extends GoogleDataprocService[F] {

  override def createCluster(
    project: GoogleProject,
    region: RegionName,
    clusterName: DataprocClusterName,
    createClusterConfig: Option[CreateClusterConfig]
  )(implicit ev: Ask[F, TraceId]): F[Option[DataprocOperation]] = {
    val config: ClusterConfig = createClusterConfig
      .map { config =>
        val bldr = ClusterConfig.newBuilder
          .setGceClusterConfig(config.gceClusterConfig)
          .addAllInitializationActions(config.nodeInitializationActions.asJava)
          .setMasterConfig(config.masterConfig)
          .setConfigBucket(config.stagingBucket.value)
          .setSoftwareConfig(config.softwareConfig)
          .setEndpointConfig(config.endpointConfig)

        config.workerConfig.foreach(bldr.setWorkerConfig)
        config.secondaryWorkerConfig.foreach(bldr.setSecondaryWorkerConfig)

        bldr.build()
      }
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

    val createCluster = for {
      client <- F.fromOption(clusterControllerClients.get(region), new Exception(s"Unsupported region ${region.value}"))
      operationOpt <- recoverF(Async[F].delay(client.createClusterAsync(request)), whenStatusCode(409))
      opAndMetadata <- operationOpt.traverse { op =>
        F.async[ClusterOperationMetadata] { cb =>
          F.delay(
            ApiFutures.addCallback(
              op.getMetadata,
              callBack(cb),
              MoreExecutors.directExecutor()
            )
          ).as(None)
        }.map(metadata => (op, metadata))
      }
    } yield opAndMetadata.map(x => DataprocOperation(OperationName(x._1.getName), x._2))

    retryF(
      createCluster,
      s"com.google.cloud.dataproc.v1.ClusterControllerClient.createClusterAsync($region, $clusterName, $createClusterConfig))"
    )
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
  ): F[DataprocOperation] =
    for {
      traceId <- ev.ask
      clusterInstances <- getClusterInstances(project, region, clusterName)
      _ <- clusterInstances.find(x => x._1.role == Master).traverse {
        case (DataprocRoleZonePreemptibility(_, zone, _), instances) =>
          instances.toList.parTraverse { instance =>
            metadata.traverse { md =>
              googleComputeService.addInstanceMetadata(
                project,
                zone,
                instance,
                md
              )
            }
          }
      }

      // First, remove preemptible instances (if any) and wait until the removal is done
      _ <-
        if (countPreemptibles(clusterInstances) > 0)
          resizeCluster(project,
                        region,
                        clusterName,
                        numWorkers = None,
                        numPreemptibles = Some(0)
          ) >> streamUntilDoneOrTimeout(
            getClusterInstances(project, region, clusterName),
            15,
            6 seconds,
            s"Timeout occurred removing preemptible instances from cluster ${project.value}/${clusterName.value}"
          )(implicitly, instances => countPreemptibles(instances) == 0)
        else F.unit

      // If removal of preemptibles is done, wait until the cluster's status transitions back to RUNNING (from UPDATING)
      // Otherwise, stopping the remaining instances may cause the cluster to get in to ERROR status
      _ <- streamUntilDoneOrTimeout(
        getCluster(project, region, clusterName),
        15,
        3 seconds,
        s"Cannot stop the instances of cluster ${project.value}/${clusterName.value} unless the cluster is in RUNNING status."
      )

      client <- F.fromOption(clusterControllerClients.get(region), new Exception(s"Unsupported region ${region.value}"))
      request =
        StopClusterRequest
          .newBuilder()
          .setProjectId(project.value)
          .setRegion(region.value)
          .setClusterName(clusterName.value)
          .setRequestId(traceId.asString)
          .build()
      fa = F.delay(client.stopClusterAsync(request))
      javaFuture <- withLogging(fa,
                                Some(traceId),
                                s"com.google.cloud.dataproc.v1.ClusterControllerClient.stopClusterAsync($request)"
      )
      res <- F
        .async[ClusterOperationMetadata] { cb =>
          F.delay(
            ApiFutures.addCallback(
              javaFuture.getMetadata,
              callBack(cb),
              MoreExecutors.directExecutor()
            )
          ).as(None)
        }
        .map(metadata => DataprocOperation(OperationName(javaFuture.getName), metadata))
    } yield res

  override def startCluster(project: GoogleProject,
                            region: RegionName,
                            clusterName: DataprocClusterName,
                            numPreemptibles: Option[Int],
                            metadata: Option[Map[String, String]]
  )(implicit
    ev: Ask[F, TraceId]
  ): F[List[Operation]] =
    for {
      clusterInstances <- getClusterInstances(project, region, clusterName)

      // Add back the preemptible instances, if any
      _ <- numPreemptibles match {
        case Some(n) =>
          resizeCluster(project,
                        region,
                        clusterName,
                        numWorkers = None,
                        numPreemptibles = Some(n)
          ) >> streamUntilDoneOrTimeout(
            getClusterInstances(project, region, clusterName),
            15,
            6 seconds,
            s"Timeout occurred adding preemptible instances to cluster ${project.value}/${clusterName.value}"
          )(implicitly, instances => countPreemptibles(instances) == n).void
        case None => F.unit
      }

      // If adding of preemptibles is done, wait until the cluster's status transitions back to RUNNING (from UPDATING)
      // Otherwise, starting the remaining instances may cause the cluster to get in to ERROR status
      _ <- streamUntilDoneOrTimeout(
        getCluster(project, region, clusterName),
        15,
        3 seconds,
        s"Cannot start the instances of cluster ${project.value}/${clusterName.value} unless the cluster is in RUNNING status."
      )

      // Then, start each remaining instance individually
      operations <- clusterInstances.toList.parFlatTraverse {
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
                } >> googleComputeService.startInstance(project, zone, instance)
              case _ =>
                googleComputeService.startInstance(project, zone, instance)
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
  ): F[Option[DataprocOperation]] = {
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
        for {
          client <- F.fromOption(clusterControllerClients.get(region),
                                 new Exception(s"Unsupported region ${region.value}")
          )

          op <- F.delay(client.updateClusterAsync(request))
          metadata <- F.async[ClusterOperationMetadata] { cb =>
            F.delay(
              ApiFutures.addCallback(
                op.getMetadata,
                callBack(cb),
                MoreExecutors.directExecutor()
              )
            ).as(None)
          }
        } yield DataprocOperation(OperationName(op.getName), metadata)
      }
      .handleErrorWith {
        case _: com.google.api.gax.rpc.NotFoundException => F.pure(none[DataprocOperation])
        case e                                           => F.raiseError[Option[DataprocOperation]](e)
      }

    retryF(updateCluster,
           s"com.google.cloud.dataproc.v1.ClusterControllerClient.updateClusterAsync($updateClusterRequest)"
    )
  }

  override def deleteCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[DataprocOperation]] = {
    val request = DeleteClusterRequest
      .newBuilder()
      .setRegion(region.value)
      .setProjectId(project.value)
      .setClusterName(clusterName.value)
      .build()

    val deleteCluster = (for {
      client <- F.fromOption(clusterControllerClients.get(region), new Exception(s"Unsupported region ${region.value}"))
      op <- F.delay(client.deleteClusterAsync(request))
      metadata <- F.async[ClusterOperationMetadata] { cb =>
        F.delay(
          ApiFutures.addCallback(
            op.getMetadata,
            callBack(cb),
            MoreExecutors.directExecutor()
          )
        ).as(None)
      }
    } yield DataprocOperation(OperationName(op.getName), metadata))
      .map(Option(_))
      .handleErrorWith {
        case _: com.google.api.gax.rpc.NotFoundException => F.pure(none[DataprocOperation])
        case e                                           => F.raiseError[Option[DataprocOperation]](e)
      }

    retryF(deleteCluster, s"com.google.cloud.dataproc.v1.ClusterControllerClient.deleteClusterAsync($request)")
  }

  override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Cluster]] = {
    val fa = for {
      client <- F.fromOption(clusterControllerClients.get(region), new Exception(s"Unsupported region ${region.value}"))
      res <- F
        .blocking(client.getCluster(project.value, region.value, clusterName.value))
        .map(Option(_))
        .handleErrorWith {
          case _: com.google.api.gax.rpc.NotFoundException => F.pure(none[Cluster])
          case e                                           => F.raiseError[Option[Cluster]](e)
        }
    } yield res

    tracedRetryF(retryConfig)(
      fa,
      s"com.google.cloud.dataproc.v1.ClusterControllerClient.getCluster(${project.value}, ${region.value}, ${clusterName.value})",
      Show.show[Option[Cluster]](c => s"${c.map(_.getStatus.getState.toString).getOrElse("Not found")}")
    ).compile.lastOrError
  }

  override def getClusterInstances(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
    implicit ev: Ask[F, TraceId]
  ): F[Map[DataprocRoleZonePreemptibility, Set[InstanceName]]] =
    for {
      cluster <- getCluster(project, region, clusterName)
    } yield cluster.map(getAllInstanceNames).getOrElse(Map.empty)

  override def getClusterError(
    region: RegionName,
    operationName: OperationName
  )(implicit ev: Ask[F, TraceId]): F[Option[ClusterError]] =
    for {
      client <- F.fromOption(clusterControllerClients.get(region), new Exception(s"Unsupported region ${region.value}"))

      error <- retryF(
        recoverF(
          F.blocking(client.getOperationsClient.getOperation(operationName.value).getError),
          whenStatusCode(404)
        ),
        s"com.google.cloud.dataproc.v1.ClusterControllerClient.getOperationsClient.getOperation(${operationName.value}).getError()"
      )
    } yield error.map(e => ClusterError(e.getCode, e.getMessage))

  private def retryF[A](fa: F[A], action: String)(implicit ev: Ask[F, TraceId]): F[A] =
    tracedRetryF(retryConfig)(blockerBound.permit.use(_ => fa), action).compile.lastOrError
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

  private[google2] def countPreemptibles(instances: Map[DataprocRoleZonePreemptibility, Set[InstanceName]]): Int =
    instances.foldLeft(0) { case (r, (DataprocRoleZonePreemptibility(_, _, isPreemptible), instanceNames)) =>
      r + (if (isPreemptible) instanceNames.size else 0)
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

  implicit private[google2] val clusterRunningCheckable: DoneCheckable[Option[Cluster]] =
    clusterOpt => clusterOpt.exists(_.getStatus.getState.toString == "RUNNING")
}
