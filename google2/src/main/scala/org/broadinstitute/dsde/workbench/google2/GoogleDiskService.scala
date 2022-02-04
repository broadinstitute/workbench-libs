package org.broadinstitute.dsde.workbench.google2

import cats.effect.std.Semaphore
import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.google.api.gax.core.{FixedCredentialsProvider, FixedExecutorProvider}
import com.google.cloud.compute.v1.Disk
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.compute.v1._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import fs2.Stream
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import java.util.concurrent.ScheduledThreadPoolExecutor
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
      scopedCredential = credential.createScoped(Seq(CLOUD_PLATFORM_SCOPE).asJava)
      interpreter <- fromCredential(scopedCredential, blockerBound, retryConfig)
    } yield interpreter

  private def fromCredential[F[_]: StructuredLogger: Async](
    googleCredentials: GoogleCredentials,
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig,
    numOfThreads: Int = 20
  ): Resource[F, GoogleDiskService[F]] = {
    val credentialsProvider = FixedCredentialsProvider.create(googleCredentials)
    val threadFactory = new ThreadFactoryBuilder().setNameFormat("goog-disk-%d").setDaemon(true).build()
    val fixedExecutorProvider =
      FixedExecutorProvider.create(new ScheduledThreadPoolExecutor(numOfThreads, threadFactory))

    val diskSettings = DisksSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .setBackgroundExecutorProvider(fixedExecutorProvider)
      .build()

    for {
      diskClient <- backgroundResourceF(DisksClient.create(diskSettings))
    } yield new GoogleDiskInterpreter[F](
      diskClient,
      retryConfig,
      blockerBound
    )
  }
}

final case class DiskName(value: String) extends AnyVal
