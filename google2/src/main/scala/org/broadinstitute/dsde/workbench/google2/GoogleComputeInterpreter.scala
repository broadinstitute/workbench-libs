package org.broadinstitute.dsde.workbench
package google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.api.gax.rpc.ApiException
import com.google.api.gax.rpc.StatusCode.Code
import com.google.cloud.compute.v1._
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchException}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.control.NonFatal

private[google2] class GoogleComputeInterpreter[F[_]: Async: Logger: Timer: ContextShift](
  instanceClient: InstanceClient,
  firewallClient: FirewallClient,
  diskClient: DiskClient,
  zoneClient: ZoneClient,
  machineTypeClient: MachineTypeClient,
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
      recoverF(Async[F].delay(instanceClient.getInstance(projectZoneInstanceName))),
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
      instanceOpt <- Async[F].delay(Option(instanceClient.getInstance(projectZoneInstanceName)))
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
                               firewall: Firewall)(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit] =
    retryF(
      Async[F].delay(firewallClient.insertFirewall(project.value, firewall)),
      s"com.google.cloud.compute.v1.FirewallClient.insertFirewall(${project.value}, ${firewall.getName})"
    ).void

  override def getFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Firewall]] = {
    val projectFirewallRuleName = ProjectGlobalFirewallName.of(firewallRuleName.value, project.value)
    retryF(
      recoverF(Async[F].delay(firewallClient.getFirewall(projectFirewallRuleName))),
      s"com.google.cloud.compute.v1.FirewallClient.insertFirewall(${project.value}, ${firewallRuleName.value})"
    )
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

  def getZones(project: GoogleProject,
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

  def getMachineType(project: GoogleProject, zone: ZoneName, machineTypeName: MachineTypeName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[MachineType]] = {
    val projectZoneMachineTypeName = ProjectZoneMachineTypeName.of(machineTypeName.value, project.value, zone.value)
    retryF(
      recoverF(Async[F].delay(machineTypeClient.getMachineType(projectZoneMachineTypeName))),
      s"com.google.cloud.compute.v1.MachineTypeClient.getMachineType(${projectZoneMachineTypeName.toString})"
    )
  }

  private def buildMachineTypeUri(zone: ZoneName, machineTypeName: MachineTypeName): String =
    s"zones/${zone.value}/machineTypes/${machineTypeName.value}"

  private def buildRegionUri(googleProject: GoogleProject, regionName: RegionName): String =
    s"https://www.googleapis.com/compute/v1/projects/${googleProject.value}/regions/${regionName.value}"

  // Retries an F[A] depending on a RetryConfig, logging each attempt. Returns the last attempt.
  private def retryF[A](fa: F[A], loggingMsg: String)(implicit ev: ApplicativeAsk[F, TraceId]): F[A] =
    tracedRetryGoogleF(retryConfig)(blockerBound.withPermit(blocker.blockOn(fa)), loggingMsg).compile.lastOrError

}
object GoogleComputeInterpreter {
  val isRetryable: Throwable => Boolean = {
    case e: ApiException => e.isRetryable()
    case other           => NonFatal(other)
  }

  val is404: Throwable => Boolean = {
    case e: ApiException if e.getStatusCode.getCode == Code.NOT_FOUND => true
    case _                                                            => false
  }

  val defaultRetryConfig = RetryConfig(
    org.broadinstitute.dsde.workbench.util2.addJitter(1 seconds, 1 seconds),
    x => x * 2,
    5,
    isRetryable
  )
}
