package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.mtl.ApplicativeAsk
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.services.compute.ComputeScopes
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.compute.v1._
import fs2._
import _root_.io.chrisdavenport.log4cats.StructuredLogger
import cats.Parallel
import org.broadinstitute.dsde.workbench.{DoneCheckable, RetryConfig}
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchEmail}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

/**
 * Algebra for Google Compute access.
 */
trait GoogleComputeService[F[_]] {
  def createInstance(project: GoogleProject, zone: ZoneName, instance: Instance)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]

  /**
   * @param autoDeleteDiskDeviceName Set of disk device names that should be marked as auto deletable when runtime is deleted
   * @return
   */
  def deleteInstance(project: GoogleProject,
                     zone: ZoneName,
                     instanceName: InstanceName,
                     autoDeleteDiskDeviceName: Set[DeviceName] = Set.empty)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]

  def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Instance]]

  def stopInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]

  def startInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]

  def addInstanceMetadata(project: GoogleProject,
                          zone: ZoneName,
                          instanceName: InstanceName,
                          metadata: Map[String, String])(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit] =
    modifyInstanceMetadata(project, zone, instanceName, metadata, Set.empty)

  def removeInstanceMetadata(project: GoogleProject,
                             zone: ZoneName,
                             instanceName: InstanceName,
                             metadataToRemove: Set[String])(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit] =
    modifyInstanceMetadata(project, zone, instanceName, Map.empty, metadataToRemove)

  def modifyInstanceMetadata(project: GoogleProject,
                             zone: ZoneName,
                             instanceName: InstanceName,
                             metadataToAdd: Map[String, String],
                             metadataToRemove: Set[String])(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]

  def addFirewallRule(project: GoogleProject, firewall: Firewall)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def getFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Firewall]]

  def deleteFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Unit]

  def getComputeEngineDefaultServiceAccount(projectNumber: Long): WorkbenchEmail =
    // Service account email format documented in:
    // https://cloud.google.com/compute/docs/access/service-accounts#compute_engine_default_service_account
    WorkbenchEmail(s"$projectNumber-compute@developer.gserviceaccount.com")

  def setMachineType(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, machineType: MachineTypeName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Unit]

  def getMachineType(project: GoogleProject, zone: ZoneName, machineTypeName: MachineTypeName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[MachineType]]

  def getZones(project: GoogleProject, regionName: RegionName)(implicit ev: ApplicativeAsk[F, TraceId]): F[List[Zone]]

  def getNetwork(project: GoogleProject, networkName: NetworkName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Network]]

  def createNetwork(project: GoogleProject, network: Network)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def getSubnetwork(project: GoogleProject, region: RegionName, subnetwork: SubnetworkName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Subnetwork]]

  def createSubnetwork(project: GoogleProject, region: RegionName, subnetwork: Subnetwork)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]

  def getZoneOperation(project: GoogleProject, zoneName: ZoneName, operationName: OperationName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]

  def getRegionOperation(project: GoogleProject, regionName: RegionName, operationName: OperationName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]

  def getGlobalOperation(project: GoogleProject, operationName: OperationName)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]

  def pollOperation(project: GoogleProject, operation: Operation, delay: FiniteDuration, maxAttempts: Int)(
    implicit ev: ApplicativeAsk[F, TraceId],
    doneEv: DoneCheckable[Operation]
  ): Stream[F, Operation]
}

object GoogleComputeService {
  def resource[F[_]: StructuredLogger: Async: Parallel: Timer: ContextShift](
    pathToCredential: String,
    blocker: Blocker,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig = RetryPredicates.standardRetryConfig
  ): Resource[F, GoogleComputeService[F]] =
    for {
      credential <- credentialResource(pathToCredential)
      scopedCredential = credential.createScoped(Seq(ComputeScopes.COMPUTE).asJava)
      interpreter <- fromCredential(scopedCredential, blocker, blockerBound, retryConfig)
    } yield interpreter

  def resourceFromUserCredential[F[_]: StructuredLogger: Async: Parallel: Timer: ContextShift](
    pathToCredential: String,
    blocker: Blocker,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig = RetryPredicates.standardRetryConfig
  ): Resource[F, GoogleComputeService[F]] =
    for {
      credential <- userCredentials(pathToCredential)
      scopedCredential = credential.createScoped(Seq(ComputeScopes.COMPUTE).asJava)
      interpreter <- fromCredential(scopedCredential, blocker, blockerBound, retryConfig)
    } yield interpreter

  private def fromCredential[F[_]: StructuredLogger: Async: Parallel: Timer: ContextShift](
    googleCredentials: GoogleCredentials,
    blocker: Blocker,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig
  ): Resource[F, GoogleComputeService[F]] = {
    val credentialsProvider = FixedCredentialsProvider.create(googleCredentials)

    val instanceSettings = InstanceSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val firewallSettings = FirewallSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val zoneSettings = ZoneSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val machineTypeSettings = MachineTypeSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val networkSettings = NetworkSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val subnetworkSettings = SubnetworkSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val zoneOperationSettings = ZoneOperationSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val regionOperationSettings = RegionOperationSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val globalOperationSettings = GlobalOperationSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()

    for {
      instanceClient <- backgroundResourceF(InstanceClient.create(instanceSettings))
      firewallClient <- backgroundResourceF(FirewallClient.create(firewallSettings))
      zoneClient <- backgroundResourceF(ZoneClient.create(zoneSettings))
      machineTypeClient <- backgroundResourceF(MachineTypeClient.create(machineTypeSettings))
      networkClient <- backgroundResourceF(NetworkClient.create(networkSettings))
      subnetworkClient <- backgroundResourceF(SubnetworkClient.create(subnetworkSettings))
      zoneOperationClient <- backgroundResourceF(ZoneOperationClient.create(zoneOperationSettings))
      regionOperationClient <- backgroundResourceF(RegionOperationClient.create(regionOperationSettings))
      globalOperationClient <- backgroundResourceF(GlobalOperationClient.create(globalOperationSettings))
    } yield new GoogleComputeInterpreter[F](instanceClient,
                                            firewallClient,
                                            zoneClient,
                                            machineTypeClient,
                                            networkClient,
                                            subnetworkClient,
                                            zoneOperationClient,
                                            regionOperationClient,
                                            globalOperationClient,
                                            retryConfig,
                                            blocker,
                                            blockerBound)
  }
}

final case class InstanceName(value: String) extends AnyVal
final case class ZoneName(value: String) extends AnyVal
final case class FirewallRuleName(value: String) extends AnyVal
final case class MachineTypeName(value: String) extends AnyVal
final case class RegionName(value: String) extends AnyVal
final case class NetworkName(value: String) extends AnyVal
final case class SubnetworkName(value: String) extends AnyVal
final case class OperationName(value: String) extends AnyVal
