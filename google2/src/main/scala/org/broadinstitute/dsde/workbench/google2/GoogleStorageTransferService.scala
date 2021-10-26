package org.broadinstitute.dsde.workbench
package google2

import com.google.`type`.Date
import com.google.longrunning.Operation
import com.google.storagetransfer.v1.proto.TransferTypes.TransferJob
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.ValueObject
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject, ServiceAccount}

import java.time.LocalDate


/**
 * Algebra for Google storage access
 *
 * We follow tagless final pattern similar to https://typelevel.org/cats-tagless/
 */
trait GoogleStorageTransferService[F[_]] {

  def getStsServiceAccount(project: GoogleProject): F[ServiceAccount]

  def createTransferJob(jobName: TransferJobName,
                        jobDescription: String,
                        projectToBill: GoogleProject,
                        originBucket: GcsBucketName,
                        destinationBucket: GcsBucketName,
                        schedule: TransferJobSchedule,
                        options: Option[TransferJobOptions] = None
                    ): F[TransferJob]

  def getTransferJob(jobName: TransferJobName, project: GoogleProject): F[TransferJob]

  def listTransferOperations(jobName: TransferJobName, project: GoogleProject): F[Iterable[Operation]]

}

object GoogleStorageTransferService {

  sealed trait TransferJobOverwriteOption

  /** Transfer objects from source if not binary equivalent to those at destination. */
  object OverwriteObjectsIfDifferent extends TransferJobOverwriteOption

  /** Always transfer objects from the source bucket, even if they exist at destination. */
  object OverwriteObjectsAlreadyExistingInSink extends TransferJobOverwriteOption


  sealed trait TransferJobDeletionOption

  /** Never delete objects from source. */
  object NeverDeleteSourceObjects extends TransferJobDeletionOption

  /** Delete objects from source after they've been transferred. */
  object DeleteSourceObjectsAfterTransfer extends TransferJobDeletionOption

  /** Delete files from destination if they're not at source. */
  object DeleteObjectsUniqueInSink extends TransferJobDeletionOption


  sealed trait TransferJobSchedule

  case class TransferOnce(date: Date) extends TransferJobSchedule

  object TransferOnce {
    def apply(date: LocalDate): TransferOnce = TransferOnce(
      Date.newBuilder
        .setYear(date.getYear)
        .setMonth(date.getMonthValue)
        .setDay(date.getMonthValue)
        .build
    )
  }

  case class TransferJobOptions(whenToOverwrite: TransferJobOverwriteOption,
                                whenToDelete: TransferJobDeletionOption
                               )

  case class TransferJobName private(value: String) extends ValueObject

  object TransferJobName {
    def apply(name: String): TransferJobName = new TransferJobName(
      if (name.startsWith("transferJobs/")) name else s"transferJobs/$name"
    )
  }

}