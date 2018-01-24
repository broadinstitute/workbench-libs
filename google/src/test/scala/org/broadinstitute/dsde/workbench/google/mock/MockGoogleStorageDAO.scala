package org.broadinstitute.dsde.workbench.google.mock

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, StringWriter}
import java.util.Base64

import com.google.api.client.util.IOUtils
import com.google.api.services.storage.model.StorageObject
import org.broadinstitute.dsde.workbench.google.GoogleStorageDAO

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mbemis on 1/12/18.
  */
class MockGoogleStorageDAO(  implicit val executionContext: ExecutionContext ) extends GoogleStorageDAO {
  val buckets: TrieMap[String, Set[(String, ByteArrayInputStream)]] = TrieMap()

  override def createBucket(billingProjectName: String, bucketName: String): Future[String] = {
    buckets.putIfAbsent(s"$billingProjectName/$bucketName", Set.empty)
    Future.successful(bucketName)
  }

  override def storeObject(bucketName: String, objectName: String, objectContents: ByteArrayInputStream, objectType: String = "text/plain"): Future[Unit] = {
    val current = buckets.get(bucketName)

    current match {
      case Some(objects) => buckets.put(bucketName, objects ++ Set((objectName, objectContents)))
      case None => buckets.put(bucketName, Set((objectName, objectContents)))
    }

    Future.successful(())
  }

  override def removeObject(bucketName: String, objectName: String): Future[Unit] = {
    val current = buckets.get(bucketName)

    current match {
      case Some(objects) => buckets.put(bucketName, objects.filter(_._1.equalsIgnoreCase(objectName)))
      case None => ()
    }

    Future.successful(())
  }

  override def getObject(bucketName: String, objectName: String): Future[Option[ByteArrayOutputStream]] = {
    val current = buckets.get(bucketName)
    val response = new ByteArrayOutputStream()

    Future {
      current match {
        case Some(objs) => {
          val objects = objs.filter(_._1.equalsIgnoreCase(objectName)).toList

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

  override def setBucketLifecycle(bucketName: String, lifecycleAge: Int, lifecycleType: String): Future[Unit] = Future.successful(())

  override def listObjectsWithPrefix(bucketName: String, objectNamePrefix: String): Future[Seq[StorageObject]] = {
    val current = buckets.get(bucketName)

    val objects = current match {
      case Some(objs) => {
        objs.filter(_._1.startsWith(objectNamePrefix)) map { x =>
          new StorageObject().setName(x._1)
        }
      }.toSeq
      case None => Seq.empty
    }

    Future.successful(objects)
  }
}
