package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import com.google.storagetransfer.v1.proto.TransferProto.GetGoogleServiceAccountRequest
import com.google.storagetransfer.v1.proto.{StorageTransferServiceClient, TransferProto}
import com.google.storagetransfer.v1.proto.TransferTypes.{GcsData, Schedule, TransferJob, TransferOptions, TransferSpec}
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject, ServiceAccount, ServiceAccountDisplayName, ServiceAccountSubjectId}
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

  override def getStsServiceAccount(project: GoogleProject): F[ServiceAccount] = {
    val request = GetGoogleServiceAccountRequest.newBuilder.setProjectId(project.value).build
    F.delay(Using.resource(StorageTransferServiceClient.create)(client => {
      val sa = client.getGoogleServiceAccount(request)
      ServiceAccount(
        ServiceAccountSubjectId(sa.getSubjectId),
        WorkbenchEmail(sa.getAccountEmail),
        ServiceAccountDisplayName(sa.getAccountEmail)
      )
    }))
  }

  override def transferBucket(jobName: String,
                              jobDescription: String,
                              projectToBill: GoogleProject,
                              originBucket: GcsBucketName,
                              destinationBucket: GcsBucketName,
                              schedule: StorageTransferJobSchedule,
                              options: Option[StorageTransferJobOptions]
                             ): F[TransferJob] = {
    val transferJob = TransferJob.newBuilder
      .setName(s"transferJobs/$jobName")
      .setDescription(jobDescription)
      .setStatus(TransferJob.Status.ENABLED)
      .setProjectId(projectToBill.value)
      .setTransferSpec(TransferSpec.newBuilder
        .setGcsDataSource(GcsData.newBuilder().setBucketName(originBucket.value).build)
        .setGcsDataSink(GcsData.newBuilder().setBucketName(destinationBucket.value).build)
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
