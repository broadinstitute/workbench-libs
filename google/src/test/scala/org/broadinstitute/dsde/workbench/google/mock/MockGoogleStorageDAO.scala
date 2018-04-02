package org.broadinstitute.dsde.workbench.google.mock

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
import java.nio.file.Files

import com.google.api.client.util.IOUtils
import org.broadinstitute.dsde.workbench.google.GoogleStorageDAO
import org.broadinstitute.dsde.workbench.model.google.GcsLifecycleTypes.{Delete, GcsLifecycleType}
import org.broadinstitute.dsde.workbench.model.google.GcsRoles.GcsRole
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsEntity, GcsObjectName, GoogleProject}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mbemis on 1/12/18.
  */
class MockGoogleStorageDAO(  implicit val executionContext: ExecutionContext ) extends GoogleStorageDAO {
  val buckets: TrieMap[GcsBucketName, Set[(GcsObjectName, ByteArrayInputStream)]] = TrieMap()

  override def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName): Future[GcsBucketName] = {
    // Note: the mock doesn't keep track of the billing project - assumes buckets are global
    buckets.putIfAbsent(bucketName, Set.empty)
    Future.successful(bucketName)
  }

  override def deleteBucket(bucketName: GcsBucketName, recurse: Boolean): Future[Unit] = {
    buckets.remove(bucketName)
    Future.successful(())
  }

  override def bucketExists(bucketName: GcsBucketName): Future[Boolean] = {
    Future.successful(buckets.contains(bucketName))
  }

  override def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: ByteArrayInputStream, objectType: String = "text/plain"): Future[Unit] = {
    val current = buckets.get(bucketName)

    current match {
      case Some(objects) => buckets.put(bucketName, objects ++ Set((objectName, objectContents)))
      case None => buckets.put(bucketName, Set((objectName, objectContents)))
    }

    Future.successful(())
  }

  override def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: File, objectType: String): Future[Unit] = {
    val current = buckets.get(bucketName)

    current match {
      case Some(objects) => buckets.put(bucketName, objects ++ Set((objectName, new ByteArrayInputStream(Files.readAllBytes(objectContents.toPath)))))
      case None => buckets.put(bucketName, Set((objectName, new ByteArrayInputStream(Files.readAllBytes(objectContents.toPath)))))
    }

    Future.successful(())
  }

  override def removeObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Unit] = {
    val current = buckets.get(bucketName)

    current.foreach { objects =>
      buckets.put(bucketName, objects.filter(_._1 != objectName))
    }

    Future.successful(())
  }

  override def getObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Option[ByteArrayOutputStream]] = {
    val current = buckets.get(bucketName)
    val response = new ByteArrayOutputStream()

    Future {
      current match {
        case Some(objs) => {
          val objects = objs.filter(_._1 == objectName).toList

          objects match {
            case obj :: Nil => {
              IOUtils.copy(obj._2, response)
              Option(response)
            }
            case obj :: more => throw new Exception("too many results")
            case _ => None
          }
        }
        case None => None
      }
    }
  }

  override def objectExists(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Boolean] = {
    Future.successful {
      buckets.get(bucketName) match {
        case Some(objects) => objects.map(_._1).contains(objectName)
        case None => false
      }
    }
  }

  override def setBucketLifecycle(bucketName: GcsBucketName, lifecycleAge: Int, lifecycleType: GcsLifecycleType = Delete): Future[Unit] = {
    Future.successful(())
  }

  override def setObjectChangePubSubTrigger(bucketName: GcsBucketName, topicName: String, eventTypes: List[String]): Future[Unit] = {
    Future.successful(())
  }

  override def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String): Future[List[GcsObjectName]] = {
    val current = buckets.get(bucketName)

    val objects = current match {
      case Some(objs) =>
        objs.map(_._1).filter(_.value.startsWith(objectNamePrefix)).toList
      case None => List.empty
    }

    Future.successful(objects)
  }

  override def copyObject(srcBucketName: GcsBucketName, srcObjectName: GcsObjectName, destBucketName: GcsBucketName, destObjectName: GcsObjectName): Future[Unit] = Future.successful(())

  override def setBucketAccessControl(bucketName: GcsBucketName, entity: GcsEntity, role: GcsRole): Future[Unit] = {
    Future.successful(())
  }

  override def removeBucketAccessControl(bucketName: GcsBucketName, entity: GcsEntity): Future[Unit] = {
    Future.successful(())
  }

  override def setObjectAccessControl(bucketName: GcsBucketName, objectName: GcsObjectName, entity: GcsEntity, role: GcsRole): Future[Unit] = {
    Future.successful(())
  }

  override def removeObjectAccessControl(bucketName: GcsBucketName, objectName: GcsObjectName, entity: GcsEntity): Future[Unit] = {
    Future.successful(())
  }

  override def setDefaultObjectAccessControl(bucketName: GcsBucketName, entity: GcsEntity, role: GcsRole): Future[Unit] = {
    Future.successful(())
  }

  override def removeDefaultObjectAccessControl(bucketName: GcsBucketName, entity: GcsEntity): Future[Unit] = {
    Future.successful(())
  }

  override def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName, readerEntity: List[GcsEntity], ownerEntity: List[GcsEntity]): Future[GcsBucketName] = {
    buckets.putIfAbsent(bucketName, Set.empty)
    Future.successful(bucketName)
  }
}
