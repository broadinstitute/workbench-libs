package org.broadinstitute.dsde.workbench.google2.mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.api.gax.longrunning.OperationFuture
import com.google.cloud.compute.v1._
import org.broadinstitute.dsde.workbench.google2.{
  DeviceName,
  DiskName,
  FirewallRuleName,
  GoogleComputeService,
  InstanceName,
  MachineTypeName,
  NetworkName,
  RegionName,
  SubnetworkName,
  ZoneName
}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class FakeGoogleComputeService extends GoogleComputeService[IO] {
  override def createInstance(project: GoogleProject, zone: ZoneName, instance: Instance)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[OperationFuture[Operation, Operation]]] =
    IO.pure(Some(new FakeComputeOperationFuture))

  override def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[OperationFuture[Operation, Operation]]] =
    IO.pure(Some(new FakeComputeOperationFuture))

  override def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Instance]] = IO.pure(None)

  override def stopInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[OperationFuture[Operation, Operation]] = IO.pure(new FakeComputeOperationFuture)

  override def startInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[OperationFuture[Operation, Operation]] = IO.pure(new FakeComputeOperationFuture)

  override def modifyInstanceMetadata(
    project: GoogleProject,
    zone: ZoneName,
    instanceName: InstanceName,
    metadataToAdd: Map[String, String],
    metadataToRemove: Set[String]
  )(implicit ev: Ask[IO, TraceId]): IO[Option[OperationFuture[Operation, Operation]]] =
    IO.pure(Some(new FakeComputeOperationFuture))

  override def addFirewallRule(project: GoogleProject, firewall: Firewall)(implicit
    ev: Ask[IO, TraceId]
  ): IO[OperationFuture[Operation, Operation]] = IO.pure(new FakeComputeOperationFuture)

  override def getFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Firewall]] = IO.pure(None)

  override def setMachineType(project: GoogleProject,
                              zone: ZoneName,
                              instanceName: InstanceName,
                              machineType: MachineTypeName
  )(implicit ev: Ask[IO, TraceId]): IO[OperationFuture[Operation, Operation]] =
    IO.pure(new FakeComputeOperationFuture)

  override def getMachineType(project: GoogleProject, zone: ZoneName, machineTypeName: MachineTypeName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[MachineType]] = IO.pure(Some(MachineType.newBuilder().setMemoryMb(7680).setGuestCpus(4).build))

  override def getZones(project: GoogleProject, regionName: RegionName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[List[Zone]] = IO.pure(List(Zone.newBuilder.setName("us-central1-a").build))

  override def deleteFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[OperationFuture[Operation, Operation]]] = IO.pure(Some(new FakeComputeOperationFuture))

  override def getNetwork(project: GoogleProject, networkName: NetworkName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Network]] = IO.pure(None)

  override def createNetwork(project: GoogleProject, network: Network)(implicit
    ev: Ask[IO, TraceId]
  ): IO[OperationFuture[Operation, Operation]] = IO.pure(new FakeComputeOperationFuture)

  override def getSubnetwork(project: GoogleProject, region: RegionName, subnetwork: SubnetworkName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Subnetwork]] = IO.pure(None)

  override def createSubnetwork(project: GoogleProject, region: RegionName, subnetwork: Subnetwork)(implicit
    ev: Ask[IO, TraceId]
  ): IO[OperationFuture[Operation, Operation]] = IO.pure(new FakeComputeOperationFuture)

  def detachDisk(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, deviceName: DeviceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[OperationFuture[Operation, Operation]]] =
    IO.pure(Some(new FakeComputeOperationFuture))

  override def deleteInstanceWithAutoDeleteDisk(
    project: GoogleProject,
    zone: ZoneName,
    instanceName: InstanceName,
    autoDeleteDisks: Set[DiskName]
  )(implicit ev: Ask[IO, TraceId]): IO[Option[OperationFuture[Operation, Operation]]] =
    IO.pure(
      Some(
        new FakeComputeOperationFuture
      )
    )

  override def setInstanceTags(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, tags: Tags)(implicit
    ev: Ask[IO, TraceId]
  ): IO[OperationFuture[Operation, Operation]] =
    IO.pure(new FakeComputeOperationFuture)
}

object FakeGoogleComputeService extends FakeGoogleComputeService
