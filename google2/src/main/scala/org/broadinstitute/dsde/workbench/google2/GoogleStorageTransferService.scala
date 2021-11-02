package org.broadinstitute.dsde.workbench
package google2

import cats.effect.{Resource, Sync}
import com.google.longrunning.Operation
import com.google.storagetransfer.v1.proto.StorageTransferServiceClient
import com.google.storagetransfer.v1.proto.TransferTypes.TransferJob
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.ValueObject
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject, ServiceAccount}

trait GoogleStorageTransferService[F[_]] {

  def getStsServiceAccount(project: GoogleProject): F[ServiceAccount]

  def createTransferJob(jobName: JobName,
                        jobDescription: String,
                        projectToBill: GoogleProject,
                        originBucket: GcsBucketName,
                        destinationBucket: GcsBucketName,
                        schedule: JobTransferSchedule
  ): F[TransferJob]

  def getTransferJob(jobName: JobName, project: GoogleProject): F[TransferJob]

  def listTransferOperations(jobName: JobName, project: GoogleProject): F[Seq[Operation]]

  def getTransferOperation(operationName: OperationName): F[Operation]
}

object GoogleStorageTransferService {

  sealed trait JobTransferSchedule extends Any

  final object JobTransferSchedule {

    /** The job should run when the job is created. */
    final object Immediately extends JobTransferSchedule
  }

  final case class JobName(value: String) extends ValueObject

  final object JobName {
    private val prefix: String = "transferJobs/"

    def fromString(name: String): Either[String, JobName] =
      if (name.startsWith(prefix)) Right(JobName(name))
      else Left(s"""Illegal job name - "$name" must start with "$prefix".""")
  }

  final case class OperationName(value: String) extends ValueObject

  final object OperationName {
    private val prefix: String = "transferOperations/"

    def fromString(name: String): Either[String, OperationName] =
      if (name.startsWith(prefix)) Right(OperationName(name))
      else Left(s"""Illegal operation name - "$name" must start with "$prefix".""")
  }

  def resource[F[_]](implicit F: Sync[F]): Resource[F, GoogleStorageTransferService[F]] =
    Resource
      .fromAutoCloseable(F.delay(StorageTransferServiceClient.create))
      .map(new GoogleStorageTransferInterpreter[F](_))
}
