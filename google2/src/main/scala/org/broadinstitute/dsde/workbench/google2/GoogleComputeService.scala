package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.mtl.ApplicativeAsk
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.services.compute.ComputeScopes
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.compute.v1._
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchEmail}

import scala.collection.JavaConverters._

/**
 * Algebra for Google Compute access.
 */
trait GoogleComputeService[F[_]] {
  def createInstance(project: GoogleProject, zone: ZoneName, instance: Instance)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]

  def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
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
                          metadata: Map[String, String])(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]

  def addFirewallRule(project: GoogleProject, firewall: Firewall)(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]

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

  def resizeDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName, newSizeGb: Int)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Unit]

  def getZones(project: GoogleProject, regionName: RegionName)(implicit ev: ApplicativeAsk[F, TraceId]): F[List[Zone]]

  def createNetwork(project: GoogleProject, network: Network)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def createSubnetwork(project: GoogleProject, region: RegionName, subnetwork: Subnetwork)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Operation]
}

object GoogleComputeService {
  def resource[F[_]: StructuredLogger: Async: Timer: ContextShift](
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

  private def fromCredential[F[_]: StructuredLogger: Async: Timer: ContextShift](
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
    val diskSettings = DiskSettings
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

    for {
      instanceClient <- resourceF(InstanceClient.create(instanceSettings))
      firewallClient <- resourceF(FirewallClient.create(firewallSettings))
      diskClient <- resourceF(DiskClient.create(diskSettings))
      zoneClient <- resourceF(ZoneClient.create(zoneSettings))
      machineTypeClient <- resourceF(MachineTypeClient.create(machineTypeSettings))
      networkClient <- resourceF(NetworkClient.create(networkSettings))
      subnetworkClient <- resourceF(SubnetworkClient.create(subnetworkSettings))
    } yield new GoogleComputeInterpreter[F](instanceClient,
                                            firewallClient,
                                            diskClient,
                                            zoneClient,
                                            machineTypeClient,
                                            networkClient,
                                            subnetworkClient,
                                            retryConfig,
                                            blocker,
                                            blockerBound)
  }
}

final case class InstanceName(value: String) extends AnyVal
final case class DiskName(value: String) extends AnyVal
final case class ZoneName(value: String) extends AnyVal
final case class FirewallRuleName(value: String) extends AnyVal
final case class MachineTypeName(value: String) extends AnyVal
final case class RegionName(value: String) extends AnyVal
