package org.broadinstitute.dsde.workbench.google

import com.google.api.services.storage.model.Bucket
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject, StorageAcl}

import scala.concurrent.Future

/**
  * Created by rtitle on 1/23/18.
  */
trait GoogleStorageDAO {

  def createBucket(googleProject: GoogleProject,
                   bucketName: GcsBucketName,
                   bucketAcls: List[StorageAcl] = List.empty,
                   defaultObjectAcls: List[StorageAcl] = List.empty,
                   lifecycle: Option[Bucket.Lifecycle] = None): Future[Bucket]

  def deleteBucket(googleProject: GoogleProject,
                   bucketName: GcsBucketName,
                   recurse: Boolean): Future[Unit]

  def setBucketAccessControl(bucketName: GcsBucketName, accessControl: StorageAcl): Future[Unit]

  def removeBucketAccessControl(bucketName: GcsBucketName, email: WorkbenchEmail): Future[Unit]

  def setObjectAccessControl(bucketName: GcsBucketName, accessControl: StorageAcl): Future[Unit]

  def removeObjectAccessControl(bucketName: GcsBucketName, email: WorkbenchEmail): Future[Unit]

  def setDefaultObjectAccessControl(bucketName: GcsBucketName, accessControl: StorageAcl): Future[Unit]

  def removeDefaultObjectAccessControl(bucket: Bucket, email: WorkbenchEmail): Future[Unit]

}
