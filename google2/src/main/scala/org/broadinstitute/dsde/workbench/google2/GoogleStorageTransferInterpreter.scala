package org.broadinstitute.dsde.workbench
package google2

import java.nio.file.Paths

import cats.effect._
import cats.effect.std.Semaphore
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.`type`.Date
import com.google.cloud.storage.{Storage, StorageOptions}
import com.google.storagetransfer.v1.proto.StorageTransferServiceClient
import com.google.storagetransfer.v1.proto.TransferTypes.GcsData
import com.google.storagetransfer.v1.proto.{TransferProto, TransferTypes}
import io.kubernetes.client.proto.Meta.Timestamp
import fs2.Stream
import fs2.io.file.Files
import io.circe.Decoder
import io.circe.fs2._
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

private[google2] class GoogleStorageTransferInterpreter[F[_]](
                                                       db: Storage,
                                                       blockerBound: Option[Semaphore[F]]
                                                     )(implicit logger: StructuredLogger[F], F: Async[F])
  extends GoogleStorageTransferService[F] {




  def makeTransferJobSchedule(schedule: StorageTransferJobSchedule): TransferTypes.Schedule = schedule match {
      case Once(time) => TransferTypes.Schedule.newBuilder()
        .setScheduleStartDate(time)
        .setScheduleEndDate(time)
        .build
    }

  override def transferBucket(jobName: String,
                              jobDescription: String,
                              projectToBill: GoogleProject,
                              originBucket: String,
                              destinationBucket: String,
                              schedule: StorageTransferJobSchedule
                             ): F[Unit] = {
  val client = StorageTransferServiceClient.create()
  val transferJob = TransferTypes.TransferJob.newBuilder
    .setName(jobName)
    .setDescription(jobDescription)
    .setProjectId(projectToBill.value)
    .setTransferSpec(TransferTypes.TransferSpec.newBuilder
      .setGcsDataSource(GcsData.newBuilder().setBucketName(originBucket).build)
      .setGcsDataSink(GcsData.newBuilder().setBucketName(destinationBucket).build)
      .build)
    .setSchedule(makeTransferJobSchedule(schedule))
    .setCreationTime(Timestamp.newBuilder.build)
    .setLastModificationTime(Timestamp.newBuilder.build)
    .setDeletionTime(Timestamp.newBuilder.build)
    .setLatestOperationName("latestOperationName-1244328885")
    .build

    val request = TransferProto.CreateTransferJobRequest.newBuilder
      .setTransferJob(transferJob)
      .build
    client.createTransferJob(request)
  }
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
