package org.broadinstitute.dsde.workbench
package google2

import cats.effect.{Async, Resource}
import com.google.`type`.Date
import com.google.longrunning.Operation
import com.google.storagetransfer.v1.proto.StorageTransferServiceClient
import com.google.storagetransfer.v1.proto.TransferTypes.TransferJob
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.ValueObject
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject, ServiceAccount}

import java.time.LocalDate

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

  def listTransferOperations(jobName: JobName, project: GoogleProject): F[Seq[Operation]]

  def getTransferOperation(operationName: OperationName): F[Operation]

}

object GoogleStorageTransferService {

  sealed trait ObjectOverwriteOption

  final object ObjectOverwriteOption {

    /** Transfer objects from source if not binary equivalent to those at destination. */
    final object OverwriteObjectsIfDifferent extends ObjectOverwriteOption

    /** Always transfer objects from the source bucket, even if they exist at destination. */
    final object OverwriteObjectsAlreadyExistingInSink extends ObjectOverwriteOption
  }

  sealed trait ObjectDeletionOption

  final object ObjectDeletionOption {

    /** Never delete objects from source. */
    final object NeverDeleteSourceObjects extends ObjectDeletionOption

    /** Delete objects from source after they've been transferred. */
    final object DeleteSourceObjectsAfterTransfer extends ObjectDeletionOption

    /** Delete files from destination if they're not at source. */
    final object DeleteObjectsUniqueInSink extends ObjectDeletionOption
  }

  sealed trait JobTransferSchedule extends Any

  final object JobTransferSchedule {

    /** The job should run at most once at the specified `Date` */
    final case class Once(date: Date) extends AnyVal with JobTransferSchedule

    final object Once {
      def apply(date: LocalDate): Once = Once(
        Date.newBuilder
          .setYear(date.getYear)
          .setMonth(date.getMonthValue)
          .setDay(date.getMonthValue)
          .build
      )
    }
  }

  case class JobTransferOptions(whenToOverwrite: ObjectOverwriteOption, whenToDelete: ObjectDeletionOption)

  private def prefix(p: String, str: String) =
    if (str.startsWith(p)) str else s"$p$str"

  case class JobName private (value: String) extends ValueObject

  object JobName {
    def apply(name: String): JobName =
      new JobName(prefix("transferJobs/", name))
  }

  case class OperationName private (value: String) extends ValueObject

  object OperationName {
    def apply(name: String): OperationName =
      new OperationName(prefix("transferOperations/", name))
  }

  def resource[F[_]: Async]: Resource[F, GoogleStorageTransferService[F]] =
    Resource
      .make(Async[F].delay(StorageTransferServiceClient.create))(client => Async[F].delay(client.close()))
      .map(new GoogleStorageTransferInterpreter[F](_))
}
