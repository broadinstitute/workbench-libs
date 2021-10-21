package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import com.google.storagetransfer.v1.proto.StorageTransferServiceClient
import com.google.storagetransfer.v1.proto.TransferTypes.{GcsData, TransferJob, TransferSpec}
import com.google.storagetransfer.v1.proto.{TransferProto, TransferTypes}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import scala.util.Using

class GoogleStorageTransferInterpreter[F[_]]()(implicit logger: StructuredLogger[F], F: Async[F])
  extends GoogleStorageTransferService[F] {

  private def makeTransferJobSchedule(schedule: StorageTransferJobSchedule): TransferTypes.Schedule = schedule match {
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
                             ): F[TransferJob] = {
    val transferJob = TransferJob.newBuilder
      .setName(jobName)
      .setDescription(jobDescription)
      .setProjectId(projectToBill.value)
      .setTransferSpec(TransferSpec.newBuilder
        .setGcsDataSource(GcsData.newBuilder().setBucketName(originBucket).build)
        .setGcsDataSink(GcsData.newBuilder().setBucketName(destinationBucket).build)
        .build)
      .setSchedule(makeTransferJobSchedule(schedule))
      .build

    val request = TransferProto.CreateTransferJobRequest.newBuilder
      .setTransferJob(transferJob)
      .build

    Async[F].delay(Using.resource(StorageTransferServiceClient.create)(_.createTransferJob(request)))
  }
}