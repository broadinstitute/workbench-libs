package org.broadinstitute.dsde.workbench.google
import com.google.api.services.storage.model.Bucket
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject, StorageAcl}

import scala.concurrent.Future

/**
  * Created by rtitle on 1/23/18.
  */
class HttpGoogleStorageDAO extends GoogleStorageDAO {
  override def createBucket(googleProject: GoogleProject, bucketName: GcsBucketName, bucketAcls: List[StorageAcl], defaultObjectAcls: List[StorageAcl], lifecycle: Option[Bucket.Lifecycle]): Future[Bucket] = ???

  override def deleteBucket(googleProject: GoogleProject, bucketName: GcsBucketName, recurse: Boolean): Future[Unit] = ???

  override def setBucketAccessControl(bucketName: GcsBucketName, accessControl: StorageAcl): Future[Unit] = ???

  override def removeBucketAccessControl(bucketName: GcsBucketName, email: WorkbenchEmail): Future[Unit] = ???

  override def setObjectAccessControl(bucketName: GcsBucketName, accessControl: StorageAcl): Future[Unit] = ???

  override def removeObjectAccessControl(bucketName: GcsBucketName, email: WorkbenchEmail): Future[Unit] = ???

  override def setDefaultObjectAccessControl(bucketName: GcsBucketName, accessControl: StorageAcl): Future[Unit] = ???

  override def removeDefaultObjectAccessControl(bucket: Bucket, email: WorkbenchEmail): Future[Unit] = ???
}
