package org.broadinstitute.dsde.workbench
package google2

import java.nio.file.Path
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Semaphore
import cats.syntax.all._
import com.google.`type`.Date
import com.google.auth.Credentials
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.{Acl, Blob, BlobId, Bucket, StorageOptions}
import com.google.cloud.{Identity, Policy}
import fs2.{Pipe, Stream}
import com.google.cloud.storage.Storage.{BucketGetOption, BucketSourceOption}
import com.google.storagetransfer.v1.proto.TransferTypes.TransferJob
import org.typelevel.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates.standardGoogleRetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}

import scala.language.higherKinds

/**
 * Algebra for Google storage access
 *
 * We follow tagless final pattern similar to https://typelevel.org/cats-tagless/
 */
trait GoogleStorageTransferService[F[_]] {
  sealed trait StorageTransferJobSchedule
  case class Once(time: Date) extends StorageTransferJobSchedule

  def transferBucket(jobName: String,
                     jobDescription: String,
                     projectToBill: GoogleProject,
                     originBucket: String,
                     destinationBucket: String,
                     schedule: StorageTransferJobSchedule
                    ): F[TransferJob]
}