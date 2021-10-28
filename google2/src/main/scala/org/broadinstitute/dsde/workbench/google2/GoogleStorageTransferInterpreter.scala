package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import com.google.longrunning.{ListOperationsRequest, Operation}
import com.google.storagetransfer.v1.proto.TransferProto._
import com.google.storagetransfer.v1.proto.TransferTypes._
import com.google.storagetransfer.v1.proto.{StorageTransferServiceClient, TransferProto}
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService.ObjectDeletionOption._
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService.ObjectOverwriteOption._
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google._

import scala.collection.JavaConverters._

final private[google2] class GoogleStorageTransferInterpreter[F[_]](client: StorageTransferServiceClient)(implicit
  F: Async[F]
) extends GoogleStorageTransferService[F] {

  private def makeSchedule(schedule: JobTransferSchedule): Schedule = schedule match {
    case JobTransferSchedule.Once(date) =>
      Schedule.newBuilder
        .setScheduleStartDate(date)
        .setScheduleEndDate(date)
        .build
  }

  private def makeTransferOptions(options: JobTransferOptions): TransferOptions = options match {
    case JobTransferOptions(overwrite, delete) =>
      TransferOptions.newBuilder
        .setOverwriteObjectsAlreadyExistingInSink(overwrite == OverwriteObjectsAlreadyExistingInSink)
        .setDeleteObjectsUniqueInSink(delete == DeleteObjectsUniqueInSink)
        .setDeleteObjectsFromSourceAfterTransfer(delete == DeleteSourceObjectsAfterTransfer)
        .build
  }

  override def getStsServiceAccount(project: GoogleProject): F[ServiceAccount] = {
    val request = GetGoogleServiceAccountRequest.newBuilder
      .setProjectId(project.value)
      .build

    F.delay {
      val sa = client.getGoogleServiceAccount(request)
      ServiceAccount(
        ServiceAccountSubjectId(sa.getSubjectId),
        WorkbenchEmail(sa.getAccountEmail),
        ServiceAccountDisplayName(sa.getAccountEmail)
      )
    }
  }

  override def createTransferJob(jobName: JobName,
                                 jobDescription: String,
                                 projectToBill: GoogleProject,
                                 originBucket: GcsBucketName,
                                 destinationBucket: GcsBucketName,
                                 schedule: JobTransferSchedule,
                                 options: Option[JobTransferOptions]
  ): F[TransferJob] = {
    val transferJob = TransferJob.newBuilder
      .setName(jobName.value)
      .setDescription(jobDescription)
      .setStatus(TransferJob.Status.ENABLED)
      .setProjectId(projectToBill.value)
      .setTransferSpec(
        TransferSpec.newBuilder
          .setGcsDataSource(GcsData.newBuilder.setBucketName(originBucket.value).build)
          .setGcsDataSink(GcsData.newBuilder.setBucketName(destinationBucket.value).build)
          .setTransferOptions(options map makeTransferOptions getOrElse TransferOptions.getDefaultInstance)
          .build
      )
      .setSchedule(makeSchedule(schedule))
      .build

    val request = TransferProto.CreateTransferJobRequest.newBuilder
      .setTransferJob(transferJob)
      .build

    F.delay {
      client.createTransferJob(request)
    }
  }

  override def getTransferJob(jobName: JobName, project: GoogleProject): F[TransferJob] = {
    val request = GetTransferJobRequest.newBuilder
      .setJobName(jobName.value)
      .setProjectId(project.value)
      .build

    F.delay {
      client.getTransferJob(request)
    }
  }

  override def listTransferOperations(jobName: JobName, project: GoogleProject): F[Seq[Operation]] = {
    val request = ListOperationsRequest.newBuilder
      .setFilter(s"""{"projectId":"$project","jobNames":["$jobName"]}""")
      .build

    F.delay {
      client.getOperationsClient.listOperations(request).iterateAll.asScala.toSeq
    }
  }

  override def getTransferOperation(operationName: OperationName): F[Operation] =
    F.delay {
      client.getOperationsClient.getOperation(operationName.value)
    }

}
