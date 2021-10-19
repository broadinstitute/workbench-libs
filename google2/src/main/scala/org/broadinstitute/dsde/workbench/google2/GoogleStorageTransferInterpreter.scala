package org.broadinstitute.dsde.workbench
package google2

import java.nio.channels.Channels
import java.nio.file.{Path, Paths}
import java.time.Instant
import fs2.io.file.Files
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Semaphore
import cats.syntax.all._
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.Storage.{BlobListOption, BlobSourceOption, BlobTargetOption, BlobWriteOption, BucketGetOption, BucketSourceOption}
import com.google.cloud.storage.{Acl, Blob, BlobId, BlobInfo, Bucket, BucketInfo, Storage, StorageOptions}
import com.google.cloud.{Identity, Policy, Role}
import fs2.{Pipe, Stream, text}
import org.typelevel.log4cats.StructuredLogger
import io.circe.Decoder
import io.circe.fs2._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates.standardGoogleRetryConfig
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchException}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsObjectName, GoogleProject}
import com.google.auth.Credentials
import com.google.storagetransfer.v1.proto.StorageTransferServiceClient
import com.google.storagetransfer.v1.proto.TransferTypes.{TransferJob, TransferSpec}

import scala.collection.JavaConverters._

private[google2] class GoogleStorageTransferInterpreter[F[_]](
                                                       db: Storage,
                                                       blockerBound: Option[Semaphore[F]]
                                                     )(implicit logger: StructuredLogger[F], F: Async[F])
  extends GoogleStorageTransferService[F] {
  override def transferBucket(jobName: String,
                               projectToBill: GoogleProject


                             ): F[Unit] =
    StorageTransferServiceClient.create().createTransferJob(
      new TransferJob.Builder()
        .setName(jobName)
        .setProjectId(projectToBill.value)
        .setTransferSpec(
          new TransferSpec.Builder()
            .
            .build()

        )

      .build()
  )
}

object GoogleStorageInterpreter {
  def apply[F[_]: Async: StructuredLogger](
                                            db: Storage,
                                            blockerBound: Option[Semaphore[F]]
                                          ): GoogleStorageInterpreter[F] =
    new GoogleStorageInterpreter(db, blockerBound)

  def storage[F[_]: Sync: Files](
                                  pathToJson: String,
                                  project: Option[GoogleProject] =
                                  None // legacy credential file doesn't have `project_id` field. Hence we need to pass in explicitly
                                ): Resource[F, Storage] =
    for {
      credential <- org.broadinstitute.dsde.workbench.util2.readFile(pathToJson)
      project <- project match { //Use explicitly passed in project if it's defined; else use `project_id` in json credential; if neither has project defined, raise error
        case Some(p) => Resource.pure[F, GoogleProject](p)
        case None    => Resource.eval(parseProject(pathToJson).compile.lastOrError)
      }
      db <- Resource.eval(
        Sync[F].delay(
          StorageOptions
            .newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(credential))
            .setProjectId(project.value)
            .build()
            .getService
        )
      )
    } yield db

  implicit val googleProjectDecoder: Decoder[GoogleProject] = Decoder.forProduct1(
    "project_id"
  )(GoogleProject.apply)

  def parseProject[F[_]: Files: Sync](pathToJson: String): Stream[F, GoogleProject] =
    Files[F]
      .readAll(Paths.get(pathToJson), 4096)
      .through(byteStreamParser)
      .through(decoder[F, GoogleProject])
}
