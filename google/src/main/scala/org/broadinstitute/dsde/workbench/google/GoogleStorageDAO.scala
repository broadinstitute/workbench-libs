package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}

import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.GcsLifecycleTypes.{Delete, GcsLifecycleType}
import org.broadinstitute.dsde.workbench.model.google.{GcsAccessControl, GcsBucketName, GcsObjectName, GoogleProject}

import scala.concurrent.Future

trait GoogleStorageDAO {

  def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName): Future[GcsBucketName]
  def deleteBucket(bucketName: GcsBucketName, recurse: Boolean): Future[Unit]
  def bucketExists(bucketName: GcsBucketName): Future[Boolean]

  def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: ByteArrayInputStream, objectType: String): Future[Unit]
  def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: String, objectType: String): Future[Unit] = {
    storeObject(bucketName, objectName, new ByteArrayInputStream(objectContents.getBytes("UTF-8")), objectType)
  }
  def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: File, objectType: String): Future[Unit]

  def removeObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Unit]
  def getObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Option[ByteArrayOutputStream]]
  def setBucketLifecycle(bucketName: GcsBucketName, lifecycleAge: Int, lifecycleType: GcsLifecycleType = Delete): Future[Unit]
  def setObjectChangePubSubTrigger(bucketName: GcsBucketName, topicName: String, eventTypes: List[String]): Future[Unit]
  def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String): Future[List[GcsObjectName]]

  def setBucketAccessControl(bucketName: GcsBucketName, accessControl: GcsAccessControl): Future[Unit]
  def removeBucketAccessControl(bucketName: GcsBucketName, email: WorkbenchEmail): Future[Unit]

  def setObjectAccessControl(bucketName: GcsBucketName, objectName: GcsObjectName, accessControl: GcsAccessControl): Future[Unit]
  def removeObjectAccessControl(bucketName: GcsBucketName, objectName: GcsObjectName, email: WorkbenchEmail): Future[Unit]

  def setDefaultObjectAccessControl(bucketName: GcsBucketName, accessControl: GcsAccessControl): Future[Unit]
  def removeDefaultObjectAccessControl(bucketName: GcsBucketName, email: WorkbenchEmail): Future[Unit]

}
