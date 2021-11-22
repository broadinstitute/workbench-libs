package org.broadinstitute.dsde.workbench.google2.mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.cloud.compute.v1.{Firewall, Instance, MachineType, Network, Operation, Subnetwork, Tags, Zone}
import org.broadinstitute.dsde.workbench.google2.{
  ComputePollOperation,
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
  ): IO[Option[Operation]] =
    IO.pure(Some(Operation.newBuilder().setId(123).setName("opName").setTargetId(258165385).build()))

  override def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Operation]] =
    IO.pure(Some(Operation.newBuilder().setId(123).setName("opName").setTargetId(258165385).build()))

  override def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Instance]] = IO.pure(None)

  override def stopInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] = IO.pure(Operation.newBuilder().setId(123).setName("opName").setTargetId(258165385).build())

  override def startInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] = IO.pure(Operation.newBuilder().setId(123).setName("opName").setTargetId(258165385).build())

  override def modifyInstanceMetadata(
    project: GoogleProject,
    zone: ZoneName,
    instanceName: InstanceName,
    metadataToAdd: Map[String, String],
    metadataToRemove: Set[String]
  )(implicit ev: Ask[IO, TraceId]): IO[Unit] =
    IO.unit

  override def addFirewallRule(project: GoogleProject, firewall: Firewall)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] = IO.pure(Operation.newBuilder().setId(123).setName("opName").setTargetId(258165385).build())

  override def getFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Firewall]] = IO.pure(None)

  override def setMachineType(project: GoogleProject,
                              zone: ZoneName,
                              instanceName: InstanceName,
                              machineType: MachineTypeName
  )(implicit ev: Ask[IO, TraceId]): IO[Unit] =
    IO.unit

  override def getMachineType(project: GoogleProject, zone: ZoneName, machineTypeName: MachineTypeName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[MachineType]] = IO.pure(Some(MachineType.newBuilder().setMemoryMb(7680).setGuestCpus(4).build))

  override def getZones(project: GoogleProject, regionName: RegionName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[List[Zone]] = IO.pure(List(Zone.newBuilder.setName("us-central1-a").build))

  override def deleteFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Unit] = IO.unit

  override def getNetwork(project: GoogleProject, networkName: NetworkName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Network]] = IO.pure(None)

  override def createNetwork(project: GoogleProject, network: Network)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] = IO.pure(Operation.newBuilder().setId(123).setName("opName").setTargetId(258165385).build())

  override def getSubnetwork(project: GoogleProject, region: RegionName, subnetwork: SubnetworkName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Subnetwork]] = IO.pure(None)

  override def createSubnetwork(project: GoogleProject, region: RegionName, subnetwork: Subnetwork)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] = IO.pure(Operation.newBuilder().setId(123).setName("opName").setTargetId(258165385).build())

  def detachDisk(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, deviceName: DeviceName)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Option[Operation]] =
    IO.pure(Some(Operation.newBuilder().setId(123).setName("opName").setTargetId(258165385).build()))

  override def deleteInstanceWithAutoDeleteDisk(
    project: GoogleProject,
    zone: ZoneName,
    instanceName: InstanceName,
    autoDeleteDisks: Set[DiskName]
  )(implicit ev: Ask[IO, TraceId], computePollOperation: ComputePollOperation[IO]): IO[Option[Operation]] =
    IO.pure(
      Some(
        Operation
          .newBuilder()
          .setId(123)
          .setName("opName")
          .setStatus(Operation.Status.DONE)
          .setTargetId(258165385)
          .build()
      )
    )

  override def setInstanceTags(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, tags: Tags)(implicit
    ev: Ask[IO, TraceId]
  ): IO[Operation] =
    IO.pure(Operation.newBuilder().setId(123).setName("opName").setTargetId(258165385).build())
}

object FakeGoogleComputeService extends FakeGoogleComputeService
