package org.broadinstitute.dsde.workbench.google2.mock

import com.google.storagetransfer.v1.proto.TransferTypes.{TransferJob, TransferOperation}
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService
import org.broadinstitute.dsde.workbench.google2.GoogleStorageTransferService._
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject, ServiceAccount}

abstract class MockGoogleStorageTransferService[F[_]] extends GoogleStorageTransferService[F] {
  override def getStsServiceAccount(project: GoogleProject): F[ServiceAccount] = ???

  override def createTransferJob(jobName: JobName,
                                 jobDescription: String,
                                 projectToBill: GoogleProject,
                                 originBucket: GcsBucketName,
                                 destinationBucket: GcsBucketName,
                                 schedule: JobTransferSchedule,
                                 options: Option[JobTransferOptions]
  ): F[TransferJob] = ???

  override def getTransferJob(jobName: JobName, project: GoogleProject): F[TransferJob] = ???

  override def listTransferOperations(jobName: JobName, project: GoogleProject): F[Seq[TransferOperation]] = ???

  override def getTransferOperation(operationName: OperationName): F[TransferOperation] = ???
}
