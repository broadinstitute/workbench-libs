package org.broadinstitute.dsde.workbench.google2

import _root_.org.typelevel.log4cats.StructuredLogger
import cats.Parallel
import cats.effect._
import cats.effect.std.Semaphore
import cats.mtl.Ask
import com.google.api.gax.core.{FixedCredentialsProvider, FixedExecutorProvider}
import com.google.api.gax.longrunning.OperationFuture
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.compute.v1._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchEmail}
import org.broadinstitute.dsde.workbench.util2.InstanceName

import java.util.concurrent.ScheduledThreadPoolExecutor
import scala.jdk.CollectionConverters._

/**
 * Algebra for Google Compute access.
 */
trait GoogleComputeService[F[_]] {
  def createInstance(project: GoogleProject, zone: ZoneName, instance: Instance)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]]

  def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]]

  /**
   * @param autoDeleteDisks Set of disk device names that should be marked as auto deletable when runtime is deleted
   * @return
   */
  def deleteInstanceWithAutoDeleteDisk(project: GoogleProject,
                                       zone: ZoneName,
                                       instanceName: InstanceName,
                                       autoDeleteDisks: Set[DeviceName]
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]]

  def detachDisk(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, deviceName: DeviceName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]]

  def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Instance]]

  def stopInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]]

  def startInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]]

  def addInstanceMetadata(project: GoogleProject,
                          zone: ZoneName,
                          instanceName: InstanceName,
                          metadata: Map[String, String]
  )(implicit ev: Ask[F, TraceId]): F[Option[OperationFuture[Operation, Operation]]] =
    modifyInstanceMetadata(project, zone, instanceName, metadata, Set.empty)

  def removeInstanceMetadata(project: GoogleProject,
                             zone: ZoneName,
                             instanceName: InstanceName,
                             metadataToRemove: Set[String]
  )(implicit ev: Ask[F, TraceId]): F[Option[OperationFuture[Operation, Operation]]] =
    modifyInstanceMetadata(project, zone, instanceName, Map.empty, metadataToRemove)

  def modifyInstanceMetadata(project: GoogleProject,
                             zone: ZoneName,
                             instanceName: InstanceName,
                             metadataToAdd: Map[String, String],
                             metadataToRemove: Set[String]
  )(implicit ev: Ask[F, TraceId]): F[Option[OperationFuture[Operation, Operation]]]

  def addFirewallRule(project: GoogleProject, firewall: Firewall)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]]

  def getFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Firewall]]

  def deleteFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[OperationFuture[Operation, Operation]]]

  def getComputeEngineDefaultServiceAccount(projectNumber: Long): WorkbenchEmail =
    // Service account email format documented in:
    // https://cloud.google.com/compute/docs/access/service-accounts#compute_engine_default_service_account
    WorkbenchEmail(s"$projectNumber-compute@developer.gserviceaccount.com")

  def setMachineType(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, machineType: MachineTypeName)(
    implicit ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]]

  def getMachineType(project: GoogleProject, zone: ZoneName, machineTypeName: MachineTypeName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[MachineType]]

  def getZones(project: GoogleProject, regionName: RegionName)(implicit ev: Ask[F, TraceId]): F[List[Zone]]

  def getNetwork(project: GoogleProject, networkName: NetworkName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Network]]

  def createNetwork(project: GoogleProject, network: Network)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]]

  def getSubnetwork(project: GoogleProject, region: RegionName, subnetwork: SubnetworkName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Subnetwork]]

  def createSubnetwork(project: GoogleProject, region: RegionName, subnetwork: Subnetwork)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]]

  /** Sets network tags on an instance */
  def setInstanceTags(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, tags: Tags)(implicit
    ev: Ask[F, TraceId]
  ): F[OperationFuture[Operation, Operation]]
}

object GoogleComputeService {
  def resource[F[_]: StructuredLogger: Async: Parallel](
    pathToCredential: String,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig = RetryPredicates.standardGoogleRetryConfig,
    numOfThreads: Int = 20
  ): Resource[F, GoogleComputeService[F]] =
    for {
      credential <- credentialResource(pathToCredential)
      scopedCredential = credential.createScoped(Seq(CLOUD_PLATFORM_SCOPE).asJava)
      interpreter <- fromCredential(scopedCredential, blockerBound, retryConfig, numOfThreads)
    } yield interpreter

  def resourceFromUserCredential[F[_]: StructuredLogger: Async: Parallel](
    pathToCredential: String,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig = RetryPredicates.standardGoogleRetryConfig,
    numOfThreads: Int = 20
  ): Resource[F, GoogleComputeService[F]] =
    for {
      credential <- userCredentials(pathToCredential)
      scopedCredential = credential.createScoped(Seq(CLOUD_PLATFORM_SCOPE).asJava)
      interpreter <- fromCredential(scopedCredential, blockerBound, retryConfig, numOfThreads)
    } yield interpreter

  def fromCredential[F[_]: StructuredLogger: Async: Parallel](
    googleCredentials: GoogleCredentials,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig,
    numOfThreads: Int = 20
  ): Resource[F, GoogleComputeService[F]] = {
    val credentialsProvider = FixedCredentialsProvider.create(googleCredentials)
    val threadFactory = new ThreadFactoryBuilder().setNameFormat("goog-compute-%d").setDaemon(true).build()
    val fixedExecutorProvider =
      FixedExecutorProvider.create(new ScheduledThreadPoolExecutor(numOfThreads, threadFactory))

    val instanceSettings = InstancesSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .setBackgroundExecutorProvider(fixedExecutorProvider)
      .build()
    val firewallSettings = FirewallsSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val zoneSettings = ZonesSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val machineTypeSettings = MachineTypesSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val networkSettings = NetworksSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val subnetworkSettings = SubnetworksSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()

    for {
      instanceClient <- backgroundResourceF(InstancesClient.create(instanceSettings))
      firewallClient <- backgroundResourceF(FirewallsClient.create(firewallSettings))
      zoneClient <- backgroundResourceF(ZonesClient.create(zoneSettings))
      machineTypeClient <- backgroundResourceF(MachineTypesClient.create(machineTypeSettings))
      networkClient <- backgroundResourceF(NetworksClient.create(networkSettings))
      subnetworkClient <- backgroundResourceF(SubnetworksClient.create(subnetworkSettings))
    } yield new GoogleComputeInterpreter[F](instanceClient,
                                            firewallClient,
                                            zoneClient,
                                            machineTypeClient,
                                            networkClient,
                                            subnetworkClient,
                                            retryConfig,
                                            blockerBound
    )
  }
}

final case class FirewallRuleName(value: String) extends AnyVal
final case class MachineTypeName(value: String) extends AnyVal
final case class NetworkName(value: String) extends AnyVal
final case class SubnetworkName(value: String) extends AnyVal
final case class OperationName(value: String) extends AnyVal

final case class RegionName(value: String) extends AnyVal
object RegionName {
  def fromUriString(uri: String): Option[RegionName] = Option(uri).flatMap { x =>
    if (x.isEmpty)
      None
    else
      x.split("/").lastOption.map(RegionName(_))
  }
}

final case class ZoneName(value: String) extends AnyVal
object ZoneName {
  def fromUriString(uri: String): Option[ZoneName] = Option(uri).flatMap { x =>
    if (x.isEmpty)
      None
    else
      x.split("/").lastOption.map(ZoneName(_))
  }
}
