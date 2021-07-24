package org.broadinstitute.dsde.workbench.google2

import cats.effect.std.Semaphore
import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.services.compute.ComputeScopes
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.compute.v1._
import fs2.Stream
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import scala.collection.JavaConverters._

/**
 * Algebra for Google Disk access.
 */
trait GoogleDiskService[F[_]] {
  def createDisk(project: GoogleProject, zone: ZoneName, disk: Disk)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Operation]]

  def deleteDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Operation]]

  def getDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Disk]]

  def listDisks(project: GoogleProject, zone: ZoneName)(implicit
    ev: Ask[F, TraceId]
  ): Stream[F, Disk]

  def resizeDisk(project: GoogleProject, zone: ZoneName, diskName: DiskName, newSizeGb: Int)(implicit
    ev: Ask[F, TraceId]
  ): F[Operation]
}

object GoogleDiskService {
  def resource[F[_]: StructuredLogger: Async](
    pathToCredential: String,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig = RetryPredicates.standardGoogleRetryConfig
  ): Resource[F, GoogleDiskService[F]] =
    for {
      credential <- credentialResource(pathToCredential)
      scopedCredential = credential.createScoped(Seq(ComputeScopes.COMPUTE).asJava)
      interpreter <- fromCredential(scopedCredential, blockerBound, retryConfig)
    } yield interpreter

  private def fromCredential[F[_]: StructuredLogger: Async](
    googleCredentials: GoogleCredentials,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig
  ): Resource[F, GoogleDiskService[F]] = {
    val credentialsProvider = FixedCredentialsProvider.create(googleCredentials)

    val diskSettings = DiskSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()

    for {
      diskClient <- backgroundResourceF(DiskClient.create(diskSettings))
    } yield new GoogleDiskInterpreter[F](
      diskClient,
      retryConfig,
      blockerBound
    )
  }
}

final case class DiskName(value: String) extends AnyVal
