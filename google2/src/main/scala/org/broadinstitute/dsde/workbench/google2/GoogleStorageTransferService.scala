package org.broadinstitute.dsde.workbench
package google2

import cats.effect.{Resource, Sync, Temporal}
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.Credentials
import com.google.storagetransfer.v1.proto.TransferTypes.{TransferJob, TransferOperation}
import com.google.storagetransfer.v1.proto.{StorageTransferServiceClient, StorageTransferServiceSettings}
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService.ObjectDeletionOption.NeverDeleteSourceObjects
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService.ObjectOverwriteOption.OverwriteObjectsIfDifferent
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.ValueObject
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject, ServiceAccount}
import org.typelevel.log4cats.StructuredLogger

trait GoogleStorageTransferService[F[_]] {

  def getStsServiceAccount(project: GoogleProject): F[ServiceAccount]

  def createTransferJob(jobName: JobName,
                        jobDescription: String,
                        projectToBill: GoogleProject,
                        originBucket: GcsBucketName,
                        destinationBucket: GcsBucketName,
                        schedule: JobTransferSchedule,
                        options: Option[JobTransferOptions] = None
  ): F[TransferJob]

  def getTransferJob(jobName: JobName, project: GoogleProject): F[TransferJob]

  def listTransferOperations(jobName: JobName, project: GoogleProject): F[Seq[TransferOperation]]

  def getTransferOperation(operationName: OperationName): F[TransferOperation]
}

object GoogleStorageTransferService {

  sealed trait JobTransferSchedule extends Product with Serializable

  final object JobTransferSchedule {

    /** The job should run when the job is created. */
    final case object Immediately extends JobTransferSchedule
  }

  final case class JobName(value: String) extends ValueObject

  final object JobName {
    private val prefix: String = "transferJobs/"

    def fromString(name: String): Either[String, JobName] =
      if (name.startsWith(prefix)) Right(JobName(name))
      else Left(s"""Illegal job name - "$name" must start with "$prefix".""")
  }

  final case class JobTransferOptions(whenToOverwrite: ObjectOverwriteOption = OverwriteObjectsIfDifferent,
                                      whenToDelete: ObjectDeletionOption = NeverDeleteSourceObjects
  )

  sealed trait ObjectOverwriteOption extends Product with Serializable

  final object ObjectOverwriteOption {

    /** Transfer objects from source if not binary equivalent to those at destination. */
    final case object OverwriteObjectsIfDifferent extends ObjectOverwriteOption

    /** Always transfer objects from the source bucket, even if they exist at destination. */
    final case object OverwriteObjectsAlreadyExistingInSink extends ObjectOverwriteOption
  }

  sealed trait ObjectDeletionOption extends Product with Serializable

  final object ObjectDeletionOption {

    /** Never delete objects from source. */
    final case object NeverDeleteSourceObjects extends ObjectDeletionOption

    /** Delete objects from source after they've been transferred. */
    final case object DeleteSourceObjectsAfterTransfer extends ObjectDeletionOption

    /** Delete files from destination if they're not at source. */
    final case object DeleteObjectsUniqueInSink extends ObjectDeletionOption
  }

  final case class OperationName(value: String) extends ValueObject

  final object OperationName {
    private val prefix: String = "transferOperations/"

    def fromString(name: String): Either[String, OperationName] =
      if (name.startsWith(prefix)) Right(OperationName(name))
      else Left(s"""Illegal operation name - "$name" must start with "$prefix".""")
  }

  def resource[F[_]](implicit
    F: Sync[F] with Temporal[F],
    logger: StructuredLogger[F]
  ): Resource[F, GoogleStorageTransferService[F]] =
    Resource
      .fromAutoCloseable(F.delay(StorageTransferServiceClient.create))
      .map(new GoogleStorageTransferInterpreter[F](_))

  def resource[F[_]](credential: Credentials)(implicit
    F: Sync[F] with Temporal[F],
    logger: StructuredLogger[F]
  ): Resource[F, GoogleStorageTransferService[F]] = {
    val settings = StorageTransferServiceSettings.newBuilder
      .setCredentialsProvider(FixedCredentialsProvider.create(credential))
      .build

    Resource
      .fromAutoCloseable(F.delay(StorageTransferServiceClient.create(settings)))
      .map(new GoogleStorageTransferInterpreter[F](_))
  }
}
