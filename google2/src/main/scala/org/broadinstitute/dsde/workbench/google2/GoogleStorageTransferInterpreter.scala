package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import com.google.`type`.Date
import com.google.longrunning.{ListOperationsRequest, Operation}
import com.google.storagetransfer.v1.proto.TransferProto._
import com.google.storagetransfer.v1.proto.TransferTypes._
import com.google.storagetransfer.v1.proto.{StorageTransferServiceClient, TransferProto}
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google._

import java.time.ZonedDateTime
import scala.collection.JavaConverters._

final private[google2] class GoogleStorageTransferInterpreter[F[_]](client: StorageTransferServiceClient)(implicit
  F: Sync[F]
) extends GoogleStorageTransferService[F] {

  private object Date {
    def fromZonedDateTime(datetime: ZonedDateTime): Date =
      com.google.`type`.Date.newBuilder
        .setYear(datetime.getYear)
        .setMonth(datetime.getMonthValue)
        .setDay(datetime.getDayOfMonth)
        .build
  }

  private def makeSchedule(schedule: JobTransferSchedule): Schedule = schedule match {
    case JobTransferSchedule.Immediately =>
      val today = Date.fromZonedDateTime(ZonedDateTime.now)
      Schedule.newBuilder
        .setScheduleStartDate(today)
        .setScheduleEndDate(today)
        .clearStartTimeOfDay()
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
                                 schedule: JobTransferSchedule
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
