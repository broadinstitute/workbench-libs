package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import com.google.storagetransfer.v1.proto.{StorageTransferServiceClient, TransferProto}
import com.google.storagetransfer.v1.proto.TransferTypes.{GcsData, Schedule, TransferJob, TransferOptions, TransferSpec}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import scala.util.Using

class GoogleStorageTransferInterpreter[F[_]]()(implicit logger: StructuredLogger[F], F: Async[F])
  extends GoogleStorageTransferService[F] {

  private def makeJobTransferSchedule(schedule: StorageTransferJobSchedule) = schedule match {
    case Once(time) => Schedule.newBuilder()
      .setScheduleStartDate(time)
      .setScheduleEndDate(time)
      .build
  }

  private def makeJobTransferOptions(options: StorageTransferJobOptions) = options match {
    case StorageTransferJobOptions(overwrite, delete) => TransferOptions.newBuilder
      .setOverwriteObjectsAlreadyExistingInSink(overwrite == OverwriteObjectsAlreadyExistingInSink)
      .setDeleteObjectsUniqueInSink(delete == DeleteObjectsUniqueInSink)
      .setDeleteObjectsFromSourceAfterTransfer(delete == DeleteSourceObjectsAfterTransfer)
      .build
  }

  override def transferBucket(jobName: String,
                              jobDescription: String,
                              projectToBill: GoogleProject,
                              originBucket: String,
                              destinationBucket: String,
                              schedule: StorageTransferJobSchedule,
                              options: Option[StorageTransferJobOptions]
                             ): F[TransferJob] = {
    val transferJob = TransferJob.newBuilder
      .setName(jobName)
      .setDescription(jobDescription)
      .setProjectId(projectToBill.value)
      .setTransferSpec(TransferSpec.newBuilder
        .setGcsDataSource(GcsData.newBuilder().setBucketName(originBucket).build)
        .setGcsDataSink(GcsData.newBuilder().setBucketName(destinationBucket).build)
        .setTransferOptions(options map makeJobTransferOptions getOrElse TransferOptions.getDefaultInstance)
        .build
      )
      .setSchedule(makeJobTransferSchedule(schedule))
      .build

    val request = TransferProto.CreateTransferJobRequest.newBuilder
      .setTransferJob(transferJob)
      .build

    F.delay(Using.resource(StorageTransferServiceClient.create)(_.createTransferJob(request)))
  }
}