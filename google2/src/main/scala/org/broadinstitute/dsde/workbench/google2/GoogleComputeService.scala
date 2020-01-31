package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.mtl.ApplicativeAsk
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.services.compute.ComputeScopes
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.google.cloud.compute.v1.{Firewall, FirewallClient, FirewallSettings, Instance, InstanceClient, InstanceSettings, Operation}
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.{TraceId, ValueObject}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.collection.JavaConverters._

trait GoogleComputeService[F[_]] {

  def createInstance(project: GoogleProject, zone: ZoneName, instance: Instance)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Instance]]

  def stopInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def startInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def addInstanceMetadata(project: GoogleProject, zone: ZoneName, instanceName: InstanceName, metadata: Map[String, String])(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]

  def addFirewallRule(project: GoogleProject, firewall: Firewall)(implicit ev: ApplicativeAsk[F, TraceId]): F[Unit]

  def getFirewallRule(project: GoogleProject, firewallRuleName: FirewallRuleName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Firewall]]

}

object GoogleComputeService {
  def fromCredentialPath[F[_]: Logger: Async: Timer: ContextShift](pathToCredential: String, blocker: Blocker, blockerBound: Semaphore[F], retryConfig: RetryConfig = GoogleComputeInterpreter.defaultRetryConfig): Resource[F, GoogleComputeService[F]] = {
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential)
      client <- fromCredential(ServiceAccountCredentials.fromStream(credentialFile).createScoped(Seq(ComputeScopes.COMPUTE).asJava), blocker, blockerBound, retryConfig)
    } yield client
  }

  def fromCredential[F[_]: Logger: Async: Timer: ContextShift](serviceAccountCredentials: GoogleCredentials, blocker: Blocker, blockerBound: Semaphore[F], retryConfig: RetryConfig = GoogleComputeInterpreter.defaultRetryConfig): Resource[F, GoogleComputeService[F]] = {
    val credentialsProvider = FixedCredentialsProvider.create(serviceAccountCredentials)
    val instanceSettings = InstanceSettings.newBuilder()
        .setCredentialsProvider(credentialsProvider)
        .build()
    val firewallSettings = FirewallSettings.newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()

    for {
      instanceClient <- Resource.make(Sync[F].delay(InstanceClient.create(instanceSettings)))(c => Sync[F].delay(IO(c.close())))
      firewallClient <- Resource.make(Sync[F].delay(FirewallClient.create(firewallSettings)))(c => Sync[F].delay(IO(c.close())))
    } yield new GoogleComputeInterpreter[F](instanceClient, firewallClient, retryConfig, blocker, blockerBound)
  }

  def fromApplicationDefault[F[_]: ContextShift: Timer: Async: Logger](blocker: Blocker, blockerBound: Semaphore[F], retryConfig: RetryConfig = GoogleComputeInterpreter.defaultRetryConfig): Resource[F, GoogleComputeService[F]] = {
    for {
      instanceClient <- Resource.make(Sync[F].delay(InstanceClient.create()))(c => Sync[F].delay(c.close()))
      firewallClient <- Resource.make(Sync[F].delay(FirewallClient.create()))(c => Sync[F].delay(c.close()))
    } yield new GoogleComputeInterpreter[F](instanceClient, firewallClient, retryConfig, blocker, blockerBound)
  }

}

final case class InstanceName(value: String) extends AnyVal
final case class ZoneName(value: String) extends AnyVal
case class FirewallRuleName(value: String) extends ValueObject