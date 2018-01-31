package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.http.{HttpResponseException, InputStreamContent}
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.plus.PlusScopes
import com.google.api.services.storage.model.Bucket.Lifecycle
import com.google.api.services.storage.model.Bucket.Lifecycle.Rule.{Action, Condition}
import com.google.api.services.storage.model.{Bucket, StorageObject}
import com.google.api.services.storage.{Storage, StorageScopes}
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mbemis on 1/8/18.
  */

class HttpGoogleStorageDAO(appName: String,
                           googleCredentialMode: GoogleCredentialMode,
                           workbenchMetricBaseName: String)
                          (implicit system: ActorSystem, executionContext: ExecutionContext)
  extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName) with GoogleStorageDAO {

  override val scopes = Seq(StorageScopes.DEVSTORAGE_FULL_CONTROL, ComputeScopes.COMPUTE, PlusScopes.USERINFO_EMAIL, PlusScopes.USERINFO_PROFILE)

  override implicit val service = GoogleInstrumentedService.Storage

  lazy val storage = {
    new Storage.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  override def createBucket(billingProjectName: String, bucketName: String): Future[String] = {
    val bucket = new Bucket().setName(bucketName)
    val inserter = storage.buckets().insert(billingProjectName, bucket)

    retryWhen500orGoogleError(() => {
      executeGoogleRequest(inserter)
      bucketName
    })
  }

  override def storeObject(bucketName: String, objectName: String, objectContents: ByteArrayInputStream, objectType: String = "text/plain"): Future[Unit] = {
    val storageObject = new StorageObject().setName(objectName)
    val media = new InputStreamContent(objectType, objectContents)
    val inserter = storage.objects().insert(bucketName, storageObject, media)
    inserter.getMediaHttpUploader.setDirectUploadEnabled(true)

    retryWhen500orGoogleError(() => {
      executeGoogleRequest(inserter)
    })
  }

  override def getObject(bucketName: String, objectName: String): Future[Option[ByteArrayOutputStream]] = {
    val getter = storage.objects().get(bucketName, objectName)
    getter.getMediaHttpDownloader.setDirectDownloadEnabled(true)

    retryWhen500orGoogleError(() => {
      try {
        val objectBytes = new ByteArrayOutputStream()
        getter.executeMediaAndDownloadTo(objectBytes)
        executeGoogleRequest(getter)
        Option(objectBytes)
      } catch {
        case t: HttpResponseException if t.getStatusCode == StatusCodes.NotFound.intValue => None
      }
    })
  }

  override def listObjectsWithPrefix(bucketName: String, objectNamePrefix: String): Future[Seq[StorageObject]] = {
    val getter = storage.objects().list(bucketName).setPrefix(objectNamePrefix)

    retryWhen500orGoogleError(() => {
      Option(executeGoogleRequest(getter).getItems.asScala).getOrElse(Seq[StorageObject]())
    })
  }

  override def removeObject(bucketName: String, objectName: String): Future[Unit] = {
    val remover = storage.objects().delete(bucketName, objectName)

    retryWhen500orGoogleError(() => {
      executeGoogleRequest(remover)
    })
  }

  //"Delete" is the only lifecycle type currently supported, so we'll default to it
  override def setBucketLifecycle(bucketName: String, lifecycleAge: Int, lifecycleType: String = "Delete"): Future[Unit] = {
    val lifecycle = new Lifecycle.Rule().setAction(new Action().setType(lifecycleType)).setCondition(new Condition().setAge(lifecycleAge))
    val bucket = new Bucket().setName(bucketName).setLifecycle(new Lifecycle().setRule(List(lifecycle).asJava))
    val updater = storage.buckets().update(bucketName, bucket)

    retryWhen500orGoogleError(() => {
      executeGoogleRequest(updater)
    })
  }

}