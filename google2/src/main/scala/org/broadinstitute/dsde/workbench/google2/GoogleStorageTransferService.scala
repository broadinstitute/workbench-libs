package org.broadinstitute.dsde.workbench
package google2

import com.google.`type`.Date
import com.google.storagetransfer.v1.proto.TransferTypes.TransferJob
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

sealed trait StorageTransferOverwriteOption

/** Transfer objects from source if not binary equivalent to those at destination. */
object OverwriteObjectsIfDifferentAtSource extends StorageTransferOverwriteOption

/** Always transfer objects from the source bucket, even if they exist at destination. */
object OverwriteObjectsAlreadyExistingInSink extends StorageTransferOverwriteOption


sealed trait StorageTransferDeletionOption

/** Never delete objects from source. */
object NeverDeleteObjects extends StorageTransferDeletionOption

/** Delete objects from source after they've been transferred. */
object DeleteSourceObjectsAfterTransfer extends StorageTransferDeletionOption

/** Delete files from destination if they're not at source. */
object DeleteObjectsUniqueInSink extends StorageTransferDeletionOption


sealed trait StorageTransferJobSchedule

case class Once(time: Date) extends StorageTransferJobSchedule


case class StorageTransferJobOptions(whenToOverwrite: StorageTransferOverwriteOption,
                                     whenToDelete: StorageTransferDeletionOption
                                    )

/**
 * Algebra for Google storage access
 *
 * We follow tagless final pattern similar to https://typelevel.org/cats-tagless/
 */
trait GoogleStorageTransferService[F[_]] {

  def transferBucket(jobName: String,
                     jobDescription: String,
                     projectToBill: GoogleProject,
                     originBucket: String,
                     destinationBucket: String,
                     schedule: StorageTransferJobSchedule,
                     options: Option[StorageTransferJobOptions] = None
                    ): F[TransferJob]
}