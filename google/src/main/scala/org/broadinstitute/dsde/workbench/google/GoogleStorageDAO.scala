package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.google.api.services.storage.model.StorageObject

import scala.concurrent.Future

/**
  * Created by mbemis on 1/8/18.
  */
trait GoogleStorageDAO {

  def createBucket(billingProjectName: String, bucketName: String): Future[String]
  def storeObject(bucketName: String, objectName: String, objectContents: ByteArrayInputStream, objectType: String = "text/plain"): Future[Unit]
  def removeObject(bucketName: String, objectName: String): Future[Unit]
  def getObject(bucketName: String, objectName: String): Future[Option[ByteArrayOutputStream]]
  def setBucketLifecycle(bucketName: String, lifecycleAge: Int, lifecycleType: String = "Delete"): Future[Unit]
  def setObjectChangePubSubTrigger(bucketName: String, topicName: String, eventTypes: Seq[String]): Future[Unit]
  def listObjectsWithPrefix(bucketName: String, objectNamePrefix: String): Future[Seq[StorageObject]]

}
