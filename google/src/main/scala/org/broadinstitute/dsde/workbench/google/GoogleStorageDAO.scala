package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}

import com.google.api.services.storage.model.Bucket
import org.broadinstitute.dsde.workbench.model.google.GcsLifecycleTypes.{Delete, GcsLifecycleType}
import org.broadinstitute.dsde.workbench.model.google.GcsRoles.GcsRole
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsEntity, GcsObjectName, GoogleProject}

import scala.concurrent.Future

trait GoogleStorageDAO {

  def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, readers: List[GcsEntity] = List.empty, owners: List[GcsEntity] = List.empty): Future[GcsBucketName]
  def getBucket(bucketName: GcsBucketName): Future[Bucket]
  def deleteBucket(bucketName: GcsBucketName, recurse: Boolean): Future[Unit]
  def bucketExists(bucketName: GcsBucketName): Future[Boolean]

  def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: ByteArrayInputStream, objectType: String): Future[Unit]
  def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: String, objectType: String): Future[Unit] = {
    storeObject(bucketName, objectName, new ByteArrayInputStream(objectContents.getBytes("UTF-8")), objectType)
  }
  def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: File, objectType: String): Future[Unit]

  def removeObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Unit]
  def getObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Option[ByteArrayOutputStream]]
  def objectExists(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Boolean]
  def setBucketLifecycle(bucketName: GcsBucketName, lifecycleAge: Int, lifecycleType: GcsLifecycleType = Delete): Future[Unit]
  def setObjectChangePubSubTrigger(bucketName: GcsBucketName, topicName: String, eventTypes: List[String]): Future[Unit]
  def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String): Future[List[GcsObjectName]]
  def copyObject(srcBucketName: GcsBucketName, srcObjectName: GcsObjectName, destBucketName: GcsBucketName, destObjectName: GcsObjectName): Future[Unit]

  def setBucketAccessControl(bucketName: GcsBucketName, entity: GcsEntity, role: GcsRole): Future[Unit]
  def removeBucketAccessControl(bucketName: GcsBucketName, entity: GcsEntity): Future[Unit]

  def setObjectAccessControl(bucketName: GcsBucketName, objectName: GcsObjectName, entity: GcsEntity, role: GcsRole): Future[Unit]
  def removeObjectAccessControl(bucketName: GcsBucketName, objectName: GcsObjectName, entity: GcsEntity): Future[Unit]

  def setDefaultObjectAccessControl(bucketName: GcsBucketName, entity: GcsEntity, role: GcsRole): Future[Unit]
  def removeDefaultObjectAccessControl(bucketName: GcsBucketName, entity: GcsEntity): Future[Unit]

}
