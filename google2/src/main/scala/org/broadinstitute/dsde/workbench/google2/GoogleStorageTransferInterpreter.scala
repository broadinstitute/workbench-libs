package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import com.google.longrunning.{GetOperationRequest, ListOperationsRequest, Operation}
import com.google.storagetransfer.v1.proto.TransferProto._
import com.google.storagetransfer.v1.proto.TransferTypes._
import com.google.storagetransfer.v1.proto.{StorageTransferServiceClient, TransferProto}
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google._
import org.typelevel.log4cats.StructuredLogger

import scala.jdk.CollectionConverters._
import scala.util.Using

class GoogleStorageTransferInterpreter[F[_]]()(implicit logger: StructuredLogger[F], F: Async[F])
  extends GoogleStorageTransferService[F] {

  private def makeJobTransferSchedule(schedule: TransferJobSchedule) = schedule match {
    case TransferOnce(date) => Schedule.newBuilder()
      .setScheduleStartDate(date)
      .setScheduleEndDate(date)
      .build
  }

  private def makeJobTransferOptions(options: TransferJobOptions) = options match {
    case TransferJobOptions(overwrite, delete) => TransferOptions.newBuilder
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

  override def createTransferJob(jobName: TransferJobName,
                                 jobDescription: String,
                                 projectToBill: GoogleProject,
                                 originBucket: GcsBucketName,
                                 destinationBucket: GcsBucketName,
                                 schedule: TransferJobSchedule,
                                 options: Option[TransferJobOptions]
                             ): F[TransferJob] = {
    val transferJob = TransferJob.newBuilder
      .setName(jobName.value)
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

  override def getTransferJob(jobName: TransferJobName, project: GoogleProject): F[TransferJob] = {
    val request = GetTransferJobRequest.newBuilder
      .setJobName(jobName.value)
      .setProjectId(project.value)
      .build

    F.delay(Using.resource(StorageTransferServiceClient.create)(_.getTransferJob(request)))
  }

  override def listTransferOperations(jobName: TransferJobName, project: GoogleProject): F[Seq[Operation]] = {
    val request = ListOperationsRequest.newBuilder
      .setFilter( s"{\"projectId\":\"$project\",\"jobNames\":[\"$jobName\"]}")
      .build

    F.delay(Using.resource(StorageTransferServiceClient.create) { client =>
      Using.resource(client.getOperationsClient)(_.listOperations(request).iterateAll.asScala.toSeq)
    })
  }

  override def getTransferOperation(operationName: TransferOperationName): F[Operation] =
    F.delay(Using.resource(StorageTransferServiceClient.create) { client =>
      Using.resource(client.getOperationsClient)(_.getOperation(operationName.value))
    })
}
