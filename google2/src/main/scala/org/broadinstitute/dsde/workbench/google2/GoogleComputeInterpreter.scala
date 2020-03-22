package org.broadinstitute.dsde.workbench
package google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.cloud.compute.v1._
import fs2._
import _root_.io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchException}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

private[google2] class GoogleComputeInterpreter[F[_]: Async: StructuredLogger: Timer: ContextShift](
  instanceClient: InstanceClient,
  firewallClient: FirewallClient,
  diskClient: DiskClient,
  zoneClient: ZoneClient,
  machineTypeClient: MachineTypeClient,
  networkClient: NetworkClient,
  subnetworkClient: SubnetworkClient,
  zoneOperationClient: ZoneOperationClient,
  regionOperationClient: RegionOperationClient,
  globalOperationClient: GlobalOperationClient,
  retryConfig: RetryConfig,
  blocker: Blocker,
  blockerBound: Semaphore[F]
) extends GoogleComputeService[F] {

  override def createInstance(project: GoogleProject, zone: ZoneName, instance: Instance)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectZone = ProjectZoneName.of(project.value, zone.value)
    retryF(
      Async[F].delay(instanceClient.insertInstance(projectZone, instance)),
      s"com.google.cloud.compute.v1.InstanceClient.insertInstance(${projectZone.toString}, ${instance.getName})"
    )
  }

  override def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    retryF(
      Async[F].delay(instanceClient.deleteInstance(projectZoneInstanceName)),
      s"com.google.cloud.compute.v1.InstanceClient.deleteInstance(${projectZoneInstanceName.toString})"
    )
  }

  override def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Instance]] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    retryF(
      recoverF(
        Async[F].delay(instanceClient.getInstance(projectZoneInstanceName)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.compute.v1.InstanceClient.getInstance(${projectZoneInstanceName.toString})"
    )
  }

  override def stopInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    retryF(
      Async[F].delay(instanceClient.stopInstance(projectZoneInstanceName)),
      s"com.google.cloud.compute.v1.InstanceClient.stopInstance(${projectZoneInstanceName.toString})"
    )
  }

  override def startInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    retryF(
      Async[F].delay(instanceClient.startInstance(projectZoneInstanceName)),
      s"com.google.cloud.compute.v1.InstanceClient.startInstance(${projectZoneInstanceName.toString})"
    )
  }

  override def addInstanceMetadata(project: GoogleProject,
                                   zone: ZoneName,
                                   instanceName: InstanceName,
                                   metadata: Map[String, String])(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    val readAndUpdate = for {
      instanceOpt <- recoverF(Async[F].delay(instanceClient.getInstance(projectZoneInstanceName)), whenStatusCode(404))
      instance <- instanceOpt.fold(
        Async[F]
          .raiseError[Instance](new WorkbenchException(s"Instance not found: ${projectZoneInstanceName.toString}"))
      )(Async[F].pure)
      curMetadataOpt = Option(instance.getMetadata)

      fingerprint = curMetadataOpt.map(_.getFingerprint).orNull
      curItems = curMetadataOpt.flatMap(m => Option(m.getItemsList)).map(_.asScala).getOrElse(List.empty)
      newMetadata = Metadata
        .newBuilder()
        .setFingerprint(fingerprint)
        .addAllItems((curItems.filterNot(i => metadata.contains(i.getKey)) ++ metadata.toList.map {
          case (k, v) =>
            Items.newBuilder().setKey(k).setValue(v).build()
        }).asJava)
        .build

      _ <- Async[F].delay(instanceClient.setMetadataInstance(projectZoneInstanceName, newMetadata))
    } yield ()

    // block and retry the read-modify-write as an atomic unit
    retryF(
      readAndUpdate,
      s"com.google.cloud.compute.v1.InstanceClient.setMetadataInstance(${projectZoneInstanceName.toString})"
    )
  }

  override def addFirewallRule(project: GoogleProject,
                               firewall: Firewall)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] =
    retryF(
      Async[F].delay(firewallClient.insertFirewall(project.value, firewall)),
      s"com.google.cloud.compute.v1.FirewallClient.insertFirewall(${project.value}, ${firewall.getName})"
    )

  override def getFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Firewall]] = {
    val projectFirewallRuleName = ProjectGlobalFirewallName.of(firewallRuleName.value, project.value)
    retryF(
      recoverF(
        Async[F].delay(firewallClient.getFirewall(projectFirewallRuleName)),
        whenStatusCode(404)
      ),
      s"com.google.cloud.compute.v1.FirewallClient.insertFirewall(${project.value}, ${firewallRuleName.value})"
    )
  }

  override def deleteFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Unit] = {
    val request =
      ProjectGlobalFirewallName.newBuilder().setProject(project.value).setFirewall(firewallRuleName.value).build
    retryF(
      recoverF(Async[F].delay(firewallClient.deleteFirewall(request)), whenStatusCode(404)),
      s"com.google.cloud.compute.v1.FirewallClient.deleteFirewall(${project.value}, ${firewallRuleName.value})"
    ).void
  }

  override def setMachineType(project: GoogleProject,
                              zone: ZoneName,
                              instanceName: InstanceName,
                              machineTypeName: MachineTypeName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    val request =
      InstancesSetMachineTypeRequest.newBuilder().setMachineType(buildMachineTypeUri(zone, machineTypeName)).build()
    retryF(
      Async[F].delay(instanceClient.setMachineTypeInstance(projectZoneInstanceName, request)),
      s"com.google.cloud.compute.v1.InstanceClient.setMachineTypeInstance(${projectZoneInstanceName.toString}, ${machineTypeName.value})"
    )
  }

  override def resizeDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName, newSizeGb: Int)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Unit] = {
    val projectZoneDiskName = ProjectZoneDiskName.of(diskName.value, project.value, zone.value)
    val request = DisksResizeRequest.newBuilder().setSizeGb(newSizeGb.toString).build()
    retryF(
      Async[F].delay(diskClient.resizeDisk(projectZoneDiskName, request)),
      s"com.google.cloud.compute.v1.DiskClient.resizeDisk(${projectZoneDiskName.toString}, $newSizeGb)"
    )
  }

  override def getZones(project: GoogleProject,
                        regionName: RegionName)(implicit ev: ApplicativeAsk[F, TraceId]): F[List[Zone]] = {
    val request = ListZonesHttpRequest
      .newBuilder()
      .setProject(project.value)
      .setFilter(s"region eq ${buildRegionUri(project, regionName)}")
      .build()

    retryF(
      Async[F].delay(zoneClient.listZones(request)),
      s"com.google.cloud.compute.v1.ZoneClient.listZones(${project.value}, ${regionName.value})"
    ).map(_.iterateAll.asScala.toList)
  }

  override def getMachineType(project: GoogleProject, zone: ZoneName, machineTypeName: MachineTypeName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[MachineType]] = {
    val projectZoneMachineTypeName = ProjectZoneMachineTypeName.of(machineTypeName.value, project.value, zone.value)
    retryF(
      recoverF(Async[F].delay(machineTypeClient.getMachineType(projectZoneMachineTypeName)), whenStatusCode(404)),
      s"com.google.cloud.compute.v1.MachineTypeClient.getMachineType(${projectZoneMachineTypeName.toString})"
    )
  }

  override def getNetwork(project: GoogleProject,
                          networkName: NetworkName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Network]] = {
    val projectNetworkName =
      ProjectGlobalNetworkName.newBuilder().setProject(project.value).setNetwork(networkName.value).build
    retryF(
      recoverF(Async[F].delay(networkClient.getNetwork(projectNetworkName)), whenStatusCode(404)),
      s"com.google.cloud.compute.v1.NetworkClient.getNetwork(${projectNetworkName.toString})"
    )
  }

  override def createNetwork(project: GoogleProject,
                             network: Network)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
    val projectName = ProjectName.newBuilder().setProject(project.value).build
    retryF(
      Async[F].delay(networkClient.insertNetwork(projectName, network)),
      s"com.google.cloud.compute.v1.NetworkClient.insertNetwork(${projectName.toString}, ${network.getName})"
    )
  }

  override def getSubnetwork(project: GoogleProject, region: RegionName, subnetwork: SubnetworkName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Subnetwork]] = {
    val projectRegionSubnetworkName = ProjectRegionSubnetworkName
      .newBuilder()
      .setProject(project.value)
      .setRegion(region.value)
      .setSubnetwork(subnetwork.value)
      .build
    retryF(
      recoverF(Async[F].delay(subnetworkClient.getSubnetwork(projectRegionSubnetworkName)), whenStatusCode(404)),
      s"com.google.cloud.compute.v1.SubnetworkClient.getSubnetwork(${projectRegionSubnetworkName.toString})"
    )
  }

  override def createSubnetwork(project: GoogleProject, region: RegionName, subnetwork: Subnetwork)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation] = {
    val projectRegionName = ProjectRegionName.newBuilder().setProject(project.value).setRegion(region.value).build
    retryF(
      Async[F].delay(subnetworkClient.insertSubnetwork(projectRegionName, subnetwork)),
      s"com.google.cloud.compute.v1.SubnetworkClient.insertSubnetwork(${projectRegionName.toString}, ${subnetwork.getName})"
    )
  }

  override def getOperation(project: GoogleProject,
                            operation: Operation)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] =
    (Option(operation.getZone), Option(operation.getRegion)) match {
      case (Some(zone), _) =>
        val request = ProjectZoneOperationName
          .newBuilder()
          .setProject(project.value)
          .setZone(zone)
          .setOperation(operation.getName)
          .build
        retryF(
          Async[F].delay(zoneOperationClient.getZoneOperation(request)),
          s"com.google.cloud.compute.v1.ZoneOperationClient.getZoneOperation(${request.toString})"
        )
      case (None, Some(region)) =>
        val request = ProjectRegionOperationName
          .newBuilder()
          .setProject(project.value)
          .setRegion(region)
          .setOperation(operation.getName)
          .build
        retryF(
          Async[F].delay(regionOperationClient.getRegionOperation(request)),
          s"com.google.cloud.compute.v1.regionOperationClient.getRegionOperation(${request.toString})"
        )
      case (None, None) =>
        val request =
          ProjectGlobalOperationName.newBuilder().setProject(project.value).setOperation(operation.getName).build
        retryF(
          Async[F].delay(globalOperationClient.getGlobalOperation(request)),
          s"com.google.cloud.compute.v1.globalOperationClient.getGlobalOperation(${request.toString})"
        )
    }

  override def pollOperation(project: GoogleProject, operation: Operation, delay: FiniteDuration, maxAttempts: Int)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): Stream[F, PollOperation] =
    (Stream.eval(getOperation(project, operation)) ++ Stream.sleep_(delay))
      .repeatN(maxAttempts)
      .map(PollOperation.fromOperation)
      .takeThrough(!_.isDone)

  private def buildMachineTypeUri(zone: ZoneName, machineTypeName: MachineTypeName): String =
    s"zones/${zone.value}/machineTypes/${machineTypeName.value}"

  private def buildRegionUri(googleProject: GoogleProject, regionName: RegionName): String =
    s"https://www.googleapis.com/compute/v1/projects/${googleProject.value}/regions/${regionName.value}"

  private def retryF[A](fa: F[A], loggingMsg: String)(implicit ev: ApplicativeAsk[F, TraceId]): F[A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(blocker.blockOn(fa)), loggingMsg).compile.lastOrError

}
