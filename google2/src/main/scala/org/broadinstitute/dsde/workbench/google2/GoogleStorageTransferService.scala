package org.broadinstitute.dsde.workbench
package google2

import com.google.`type`.Date
import com.google.storagetransfer.v1.proto.TransferTypes.TransferJob
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

sealed trait StorageTransferJobSchedule
case class Once(time: Date) extends StorageTransferJobSchedule

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
                     schedule: StorageTransferJobSchedule
                    ): F[TransferJob]
}