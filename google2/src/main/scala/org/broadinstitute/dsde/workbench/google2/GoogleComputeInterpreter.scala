package org.broadinstitute.dsde.workbench
package google2

import _root_.org.typelevel.log4cats.StructuredLogger
import cats.effect.Async
import cats.effect.std.Semaphore
import cats.mtl.Ask
import cats.syntax.all._
import cats.{Parallel, Show}
import com.google.api.gax.longrunning.OperationFuture
import com.google.api.gax.rpc.ApiException
import com.google.cloud.compute.v1._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchException}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

private[google2] class GoogleComputeInterpreter[F[_]: Parallel: StructuredLogger](
  instanceClient: InstancesClient,
  firewallClient: FirewallsClient,
  zoneClient: ZonesClient,
  machineTypeClient: MachineTypesClient,
  networkClient: NetworksClient,
  subnetworkClient: SubnetworksClient,
  retryConfig: RetryConfig,
  blockerBound: Semaphore[F]
)(implicit F: Async[F])
    extends GoogleComputeService[F] {
  override def createInstance(project: GoogleProject, zone: ZoneName, instance: Instance)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]] =
    retryF(
      recoverF(F.delay(instanceClient.insertAsync(project.value, zone.value, instance)), whenStatusCode(409)),
      s"com.google.cloud.compute.v1.InstancesClient.insertInstance(${project.value}, ${zone.value}, ${instance.getName})"
    )

  override def deleteInstanceWithAutoDeleteDisk(project: GoogleProject,
                                                zone: ZoneName,
                                                instanceName: InstanceName,
                                                autoDeleteDisks: Set[DiskName]
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]] =
    for {
      traceId <- ev.ask
      _ <- autoDeleteDisks.toList.parTraverse { diskName =>
        for {
          opFuture <- withLogging(
            F.delay(
              instanceClient
                .setDiskAutoDeleteAsync(project.value, zone.value, instanceName.value, true, diskName.value)
            ),
            Some(traceId),
            s"com.google.cloud.compute.v1.InstancesClient.setDiskAutoDelete(${project.value}, ${zone.value}, ${instanceName.value}, true, ${diskName.value})"
          )
          res <- F.blocking(opFuture.get())
          _ <- F.raiseUnless(isSuccess(res.getHttpErrorStatusCode))(new Exception(s"setDiskAutoDeleteAsync failed"))
        } yield ()
      }
      deleteOp <- deleteInstance(project, zone, instanceName)
    } yield deleteOp

  override def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]] = {
    val fa = F
      .delay(instanceClient.deleteAsync(project.value, zone.value, instanceName.value))
      .map(Option(_))
      .handleErrorWith {
        case _: com.google.api.gax.rpc.NotFoundException => F.pure(none[OperationFuture[Operation, Operation]])
        case e => F.raiseError[Option[OperationFuture[Operation, Operation]]](e)
      }

    for {
      traceId <- ev.ask
      op <- withLogging(
        fa,
        Some(traceId),
        s"com.google.cloud.compute.v1.InstancesClient.deleteInstance(${project.value}, ${zone.value}, ${instanceName.value})"
      )
    } yield op
  }

  override def detachDisk(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, deviceName: DeviceName)(
    implicit ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]] = {
    val fa = F
      .delay(
        instanceClient
          .detachDiskAsync(project.value, zone.value, instanceName.value, deviceName.asString)
      )
      .map(Option(_))
      .handleErrorWith {
        case _: com.google.api.gax.rpc.NotFoundException => F.pure(none[OperationFuture[Operation, Operation]])
        case e => F.raiseError[Option[OperationFuture[Operation, Operation]]](e)
      }

    for {
      traceId <- ev.ask
      op <- withLogging(
        fa,
        Some(traceId),
        s"com.google.cloud.compute.v1.InstancesClient.detachDiskInstance(${project.value}, ${zone.value}, ${instanceName.value}, ${deviceName.asString})"
      )
    } yield op
  }

  override def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Instance]] =
    ev.ask
      .flatMap { traceId =>
        withLogging(
          F.delay(instanceClient.get(project.value, zone.value, instanceName.value))
            .map(Option(_))
            .handleErrorWith {
              case e: ApiException if e.getStatusCode.getCode.getHttpStatusCode == 404 => F.pure(none[Instance])
              case e: com.google.api.gax.rpc.PermissionDeniedException
                  if e.getCause.getMessage.contains("requires billing to be enabled") =>
                F.pure(none[Instance])
              case e => F.raiseError[Option[Instance]](e)
            },
          Some(traceId),
          s"com.google.cloud.compute.v1.InstancesClient.getInstance(${project.value}, ${zone.value}, ${instanceName.value})",
          Show.show[Option[Instance]](c => s"${c.map(_.getStatus).getOrElse("Not Found")}")
        )
      }

  override def stopInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]] =
    retryF(
      F.delay(instanceClient.stopAsync(project.value, zone.value, instanceName.value)),
      s"com.google.cloud.compute.v1.InstancesClient.stopInstance(${project.value}, ${zone.value}, ${instanceName.value})"
    )

  override def startInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]] =
    retryF(
      F.delay(instanceClient.startAsync(project.value, zone.value, instanceName.value)),
      s"com.google.cloud.compute.v1.InstancesClient.startInstance(${project.value}, ${zone.value}, ${instanceName.value})"
    )

  override def modifyInstanceMetadata(
    project: GoogleProject,
    zone: ZoneName,
    instanceName: InstanceName,
    metadataToAdd: Map[String, String],
    metadataToRemove: Set[String]
  )(implicit ev: Ask[F, TraceId]): F[Option[OperationFuture[Operation, Operation]]] = {
    val readAndUpdate = for {
      instanceOpt <- recoverF(F.delay(instanceClient.get(project.value, zone.value, instanceName.value)),
                              whenStatusCode(404)
      )
      instance <- F.fromEither(
        instanceOpt.toRight(
          new WorkbenchException(s"Instance not found: ${project.value}, ${zone.value}, ${instanceName.value}")
        )
      )
      curMetadataOpt = Option(instance.getMetadata)

      fingerprint = curMetadataOpt.map(_.getFingerprint).orNull
      curItems = curMetadataOpt.flatMap(m => Option(m.getItemsList)).map(_.asScala).getOrElse(List.empty)
      filteredItems = curItems.filterNot { i =>
        metadataToRemove.contains(i.getKey) || metadataToAdd.contains(i.getKey)
      }
      newItems = filteredItems ++ metadataToAdd.toList.map { case (k, v) =>
        Items.newBuilder().setKey(k).setValue(v).build()
      }
      // Only make google call if there is a change
      op <-
        if (!newItems.equals(curItems)) {
          F.delay(
            instanceClient.setMetadataAsync(project.value,
                                            zone.value,
                                            instanceName.value,
                                            Metadata
                                              .newBuilder()
                                              .setFingerprint(fingerprint)
                                              .addAllItems(newItems.asJava)
                                              .build
            )
          ).map(op => op.some)
        } else F.pure(none[OperationFuture[Operation, Operation]])
    } yield op

    // block and retry the read-modify-write as an atomic unit
    retryF(
      readAndUpdate,
      s"com.google.cloud.compute.v1.InstancesClient.setMetadataInstance(${project.value}, ${zone.value}, ${instanceName.value})"
    )
  }

  override def addFirewallRule(project: GoogleProject, firewall: Firewall)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]] =
    retryF(
      F.delay(firewallClient.insertAsync(project.value, firewall)),
      s"com.google.cloud.compute.v1.FirewallsClient.insertFirewall(${project.value}, ${firewall.getName})"
    )

  override def getFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Firewall]] = {
    val request = GetFirewallRequest.newBuilder().setFirewall(firewallRuleName.value).setProject(project.value).build()
    retryF(
      recoverF(
        F.delay(firewallClient.get(request)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.compute.v1.FirewallsClient.get(${project.value}, ${firewallRuleName.value})"
    )
  }

  override def deleteFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]] = {
    val request =
      DeleteFirewallRequest.newBuilder().setProject(project.value).setFirewall(firewallRuleName.value).build
    retryF(
      recoverF(F.delay(firewallClient.deleteAsync(request)), whenStatusCode(404)),
      s"com.google.cloud.compute.v1.FirewallsClient.deleteFirewall(${project.value}, ${firewallRuleName.value})"
    )
  }

  override def setMachineType(project: GoogleProject,
                              zone: ZoneName,
                              instanceName: InstanceName,
                              machineTypeName: MachineTypeName
  )(implicit ev: Ask[F, TraceId]): F[OperationFuture[Operation, Operation]] = {
    val request =
      InstancesSetMachineTypeRequest.newBuilder().setMachineType(buildMachineTypeUri(zone, machineTypeName)).build()
    retryF(
      F.delay(
        instanceClient.setMachineTypeAsync(project.value, zone.value, instanceName.value, request)
      ),
      s"com.google.cloud.compute.v1.InstancesClient.setMachineTypeInstance(${project.value}, ${zone.value}, ${instanceName.value}, ${machineTypeName.value})"
    )
  }

  override def getZones(project: GoogleProject, regionName: RegionName)(implicit ev: Ask[F, TraceId]): F[List[Zone]] = {
    val request = ListZonesRequest
      .newBuilder()
      .setProject(project.value)
      .setFilter(s"region eq ${buildRegionUri(project, regionName)}")
      .build()

    retryF(
      F.delay(zoneClient.list(request)),
      s"com.google.cloud.compute.v1.ZonesClient.listZones(${project.value}, ${regionName.value})"
    ).map(_.iterateAll.asScala.toList)
  }

  override def getMachineType(project: GoogleProject, zone: ZoneName, machineTypeName: MachineTypeName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[MachineType]] =
    retryF(
      recoverF(F.delay(machineTypeClient.get(project.value, zone.value, machineTypeName.value)), whenStatusCode(404)),
      s"com.google.cloud.compute.v1.MachineTypesClient.getMachineType(${project.value}, ${zone.value}, ${machineTypeName.value})"
    )

  override def getNetwork(project: GoogleProject, networkName: NetworkName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Network]] =
    retryF(
      recoverF(F.delay(networkClient.get(project.value, networkName.value)), whenStatusCode(404)),
      s"com.google.cloud.compute.v1.NetworksClient.getNetwork(${project.value}, ${networkName.value})"
    )

  override def createNetwork(project: GoogleProject, network: Network)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]] =
    retryF(
      F.delay(networkClient.insertAsync(project.value, network)),
      s"com.google.cloud.compute.v1.NetworksClient.insertNetwork(${project.toString}, ${network.getName})"
    )

  override def getSubnetwork(project: GoogleProject, region: RegionName, subnetwork: SubnetworkName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Subnetwork]] =
    retryF(
      recoverF(F.delay(subnetworkClient.get(project.value, region.value, subnetwork.value)), whenStatusCode(404)),
      s"com.google.cloud.compute.v1.SubnetworksClient.getSubnetwork(${project.toString}, ${region.value}, ${subnetwork.value})"
    )

  override def createSubnetwork(project: GoogleProject, region: RegionName, subnetwork: Subnetwork)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]] =
    retryF(
      F.delay(subnetworkClient.insertAsync(project.value, region.value, subnetwork)),
      s"com.google.cloud.compute.v1.SubnetworksClient.insertSubnetwork(${project.value}, ${region.value}, ${subnetwork.getName})"
    )

  /** Sets network tags on an instance */
  override def setInstanceTags(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, tags: Tags)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]] =
    retryF(
      F.delay(instanceClient.setTagsAsync(project.value, zone.value, instanceName.value, tags)),
      s"com.google.compute.v1.InstancesClient.setTags(${project.value}, ${zone.value}, ${instanceName.value}, [${tags.getItemsList.asScala
        .mkString(", ")}]"
    )

  private def buildMachineTypeUri(zone: ZoneName, machineTypeName: MachineTypeName): String =
    s"zones/${zone.value}/machineTypes/${machineTypeName.value}"

  private def buildRegionUri(googleProject: GoogleProject, regionName: RegionName): String =
    s"https://www.googleapis.com/compute/v1/projects/${googleProject.value}/regions/${regionName.value}"

  private def retryF[A](fa: F[A], loggingMsg: String)(implicit ev: Ask[F, TraceId]): F[A] =
    tracedRetryF(retryConfig)(blockerBound.permit.use(_ => fa), loggingMsg).compile.lastOrError
}

// device name that's known to the instance (same device name when you create runtime)
final case class DeviceName(asString: String) extends AnyVal
