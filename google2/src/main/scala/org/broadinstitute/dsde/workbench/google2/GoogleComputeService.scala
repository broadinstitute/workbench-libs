package org.broadinstitute.dsde.workbench.google2

import com.google.cloud.compute.v1._
import org.broadinstitute.dsde.workbench.model.{ValueObject, WorkbenchEmail}
import org.broadinstitute.dsde.workbench.model.google.{GcsPath, GoogleProject}

import scala.collection.JavaConverters._

trait GoogleComputeService[F[_]] {

  def createInstance(googleProject: GoogleProject, zoneId: ZoneId, instanceName: InstanceName, createInstanceConfig: CreateInstanceConfig): F[Operation] = {
    val instanceBuilder = Instance.newBuilder()
      .setName(instanceName.value)
      .setMachineType(buildMachineTypeUrl(googleProject, zoneId, createInstanceConfig))
      .addNetworkInterfaces(buildNetworkInterface(createInstanceConfig).build())
      .setTags(Tags.newBuilder().addAllItems(createInstanceConfig.networkTags.map(_.value).asJava).build())
      .addDisks(buildDisks(googleProject, zoneId, instanceName, createInstanceConfig).build())
      .addServiceAccounts(buildServiceAccount(createInstanceConfig).build())

    // TODO
    // metadata - startup script

    ???

  }

  // TODO monitor instance

  // TODO stop instance

  // TODO start instance

  // TODO delete instance

  // TODO get status

  // TODO firewall rule



  private def buildMachineTypeUrl(googleProject: GoogleProject, zoneId: ZoneId, createInstanceConfig: CreateInstanceConfig): String = {
    s"https://www.googleapis.com/compute/v1/projects/${googleProject.value}/zones/${zoneId.value}/machineTypes/${createInstanceConfig.machineType.value}"
  }

  private def buildDiskTypeUrl(googleProject: GoogleProject, zoneId: ZoneId): String = {
    s"https://www.googleapis.com/compute/v1/projects/${googleProject.value}/zones/${zoneId.value}/diskTypes/pd-standard"
  }

  private def buildNetworkInterface(createInstanceConfig: CreateInstanceConfig): NetworkInterface.Builder = {
    val networkBuilder = {
      val baseConfig = NetworkInterface.newBuilder()

      createInstanceConfig.clusterVPCSettings match {
        case Some(Right(subnet)) =>
          baseConfig.setSubnetwork(subnet.value)
        case Some(Left(network)) =>
          baseConfig.setNetwork(network.value)
        case _ =>
          baseConfig
      }
    }

    // TODO: needed?
    val accessConfig = AccessConfig.newBuilder()
      .setType("ONE_TO_ONE_NAT")
      .setName("External NAT")

    networkBuilder.addAccessConfigs(accessConfig.build())
  }

  private def buildDisks(googleProject: GoogleProject, zoneId: ZoneId, instanceName: InstanceName, createInstanceConfig: CreateInstanceConfig): AttachedDisk.Builder = {
    AttachedDisk.newBuilder()
      .setBoot(true)
      .setAutoDelete(true)
      .setType("PERSISTENT")
      .setInitializeParams(AttachedDiskInitializeParams.newBuilder()
        .setDiskName(instanceName.value)
        .setSourceImage("projects/debian-cloud/global/images/family/debian-9")
        .setDiskType(buildDiskTypeUrl(googleProject, zoneId))
        .setDiskSizeGb(createInstanceConfig.diskSizeGb.toString)
        .build())
  }

  private def buildServiceAccount(createInstanceConfig: CreateInstanceConfig): ServiceAccount.Builder = {
    ServiceAccount.newBuilder()
      .setEmail(createInstanceConfig.serviceAccountEmail.map(_.value).getOrElse("default"))
      .addAllScopes(createInstanceConfig.serviceAccountScopes.asJava)
  }

}

case class CreateInstanceConfig(machineType: MachineType,
                                diskSizeGb: Int,
                                clusterVPCSettings: Option[Either[VPCNetworkName, VPCSubnetName]],
                                networkTags: List[NetworkTag],
                                serviceAccountEmail: Option[WorkbenchEmail],
                                serviceAccountScopes: List[String],
                                startupScript: Option[GcsPath]
                               )

case class InstanceName(value: String) extends ValueObject
case class VPCNetworkName(value: String) extends ValueObject
case class VPCSubnetName(value: String) extends ValueObject
case class NetworkTag(value: String) extends ValueObject
case class ZoneId(value: String) extends ValueObject
case class MachineType(value: String) extends ValueObject