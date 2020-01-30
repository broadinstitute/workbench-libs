package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.mtl.ApplicativeAsk
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.services.compute.ComputeScopes
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.google.cloud.compute.v1.{Instance, InstanceClient, InstanceSettings, Operation}
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.collection.JavaConverters._

trait GoogleComputeService[F[_]] {

  def createInstance(project: GoogleProject, zone: ZoneName, instance: Instance)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Instance]]

  def stopInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

  def startInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation]

}

object GoogleComputeService {
  def fromCredentialPath[F[_]: Logger: Async: Timer: ContextShift](pathToCredential: String, blocker: Blocker, blockerBound: Semaphore[F], retryConfig: RetryConfig = GoogleComputeInterpreter.defaultRetryConfig): Resource[F, GoogleComputeService[F]] = {
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential)
      client <- fromCredential(ServiceAccountCredentials.fromStream(credentialFile).createScoped(Seq(ComputeScopes.COMPUTE).asJava), blocker, blockerBound, retryConfig)
    } yield client
  }

  def fromCredential[F[_]: Logger: Async: Timer: ContextShift](serviceAccountCredentials: GoogleCredentials, blocker: Blocker, blockerBound: Semaphore[F], retryConfig: RetryConfig = GoogleComputeInterpreter.defaultRetryConfig): Resource[F, GoogleComputeService[F]] = {
    val settings = InstanceSettings.newBuilder()
        .setCredentialsProvider(FixedCredentialsProvider.create(serviceAccountCredentials))
        .build()

    for {
      client <- Resource.make(Sync[F].delay(InstanceClient.create(settings)))(c => Sync[F].delay(IO(c.close())))
    } yield new GoogleComputeInterpreter[F](client, retryConfig, blocker, blockerBound)
  }

  def fromApplicationDefault[F[_]: ContextShift: Timer: Async: Logger](blocker: Blocker, blockerBound: Semaphore[F], retryConfig: RetryConfig = GoogleComputeInterpreter.defaultRetryConfig): Resource[F, GoogleComputeService[F]] = {
    for {
      client <- Resource.make(Sync[F].delay(InstanceClient.create()))(c => Sync[F].delay(c.close()))
    } yield new GoogleComputeInterpreter[F](client, retryConfig, blocker, blockerBound)
  }

}

final case class InstanceName(value: String) extends AnyVal
final case class ZoneName(value: String) extends AnyVal
