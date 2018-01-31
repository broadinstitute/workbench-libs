package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.ActorMaterializer
import cats.implicits._
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.{AbstractInputStreamContent, FileContent, HttpResponseException, InputStreamContent}
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.plus.PlusScopes
import com.google.api.services.storage.model._
import com.google.api.services.storage.model.Bucket.Lifecycle
import com.google.api.services.storage.model.Bucket.Lifecycle.Rule.{Action, Condition}
import com.google.api.services.storage.{Storage, StorageScopes}
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.GcsLifecycleTypes.{Delete, GcsLifecycleType}
import org.broadinstitute.dsde.workbench.model.google._
import org.broadinstitute.dsde.workbench.util.FutureSupport

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class HttpGoogleStorageDAO(serviceAccountClientId: String,
                           pemFile: String,
                           appName: String,
                           override val workbenchMetricBaseName: String,
                           maxPageSize: Long = 1000)( implicit val system: ActorSystem, implicit val executionContext: ExecutionContext ) extends GoogleStorageDAO with FutureSupport with GoogleUtilities {

  val storageScopes = Seq(StorageScopes.DEVSTORAGE_FULL_CONTROL, ComputeScopes.COMPUTE, PlusScopes.USERINFO_EMAIL, PlusScopes.USERINFO_PROFILE)

  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val jsonFactory = JacksonFactory.getDefaultInstance

  implicit val service = GoogleInstrumentedService.Storage

  private lazy val storage: Storage = {
    new Storage.Builder(httpTransport, jsonFactory, bucketServiceAccountCredential).setApplicationName(appName).build()
  }

  private lazy val bucketServiceAccountCredential: Credential = {
    new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(serviceAccountClientId)
      .setServiceAccountScopes(storageScopes.asJava) // grant bucket-creation powers
      .setServiceAccountPrivateKeyFromPemFile(new java.io.File(pemFile))
      .build()
  }

  override def createBucket(billingProject: GoogleProject, bucketName: GcsBucketName): Future[GcsBucketName] = {
    val bucket = new Bucket().setName(bucketName.value)
    val inserter = storage.buckets().insert(billingProject.value, bucket)

    retryWhen500orGoogleError(() => {
      executeGoogleRequest(inserter)
      bucketName
    })
  }

  override def deleteBucket(bucketName: GcsBucketName, recurse: Boolean): Future[Unit] = {
    // If `recurse` is true, first delete all objects in the bucket
    val deleteObjectsFuture = if (recurse) {
      val listObjectsRequest = storage.objects().list(bucketName.value)
      retryWhen500orGoogleError(() => executeGoogleRequest(listObjectsRequest)).flatMap { objects =>
        // Handle null responses from Google
        val items = Option(objects).flatMap(objs => Option(objs.getItems)).map(_.asScala).getOrElse(Seq.empty)
        Future.traverse(items) { item =>
          removeObject(bucketName, GcsObjectName(item.getName))
        }
      }.void
    } else Future.successful(())

    deleteObjectsFuture.flatMap { _ =>
      val deleteBucketRequest = storage.buckets().delete(bucketName.value)
      retryWithRecoverWhen500orGoogleError { () =>
        executeGoogleRequest(deleteBucketRequest)
        ()
      } {
        case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
      }
    }
  }

  override def bucketExists(bucketName: GcsBucketName): Future[Boolean] = {
    val getter = storage.buckets().get(bucketName.value)
    retryWithRecoverWhen500orGoogleError { () =>
      executeGoogleRequest(getter)
      true
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => false
    }
  }

  override def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: ByteArrayInputStream, objectType: String): Future[Unit] = {
    storeObject(bucketName, objectName, new InputStreamContent(objectType, objectContents))
  }

  override def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, objectContents: File, objectType: String): Future[Unit] = {
    storeObject(bucketName, objectName, new FileContent(objectType, objectContents))
  }

  private def storeObject(bucketName: GcsBucketName, objectName: GcsObjectName, content: AbstractInputStreamContent): Future[Unit] = {
    val storageObject = new StorageObject().setName(objectName.value)
    val inserter = storage.objects().insert(bucketName.value, storageObject, content)
    inserter.getMediaHttpUploader.setDirectUploadEnabled(true)

    retryWhen500orGoogleError(() => {
      executeGoogleRequest(inserter)
    })
  }

  override def getObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Option[ByteArrayOutputStream]] = {
    val getter = storage.objects().get(bucketName.value, objectName.value)
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

  //This functionality doesn't exist in the com.google.apis Java library.
  //When we migrate to the com.google.cloud library, we will be able to re-write this to use their implementation
  override def setObjectChangePubSubTrigger(bucketName: GcsBucketName, topicName: String, eventTypes: List[String]): Future[Unit] = {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import org.broadinstitute.dsde.workbench.google.GoogleRequestJsonSupport._
    import spray.json._
    implicit val materializer = ActorMaterializer()

    bucketServiceAccountCredential.refreshToken()

    val url = s"https://www.googleapis.com/storage/v1/b/$bucketName/notificationConfigs"
    val header = headers.Authorization(OAuth2BearerToken(bucketServiceAccountCredential.getAccessToken))

    val entity = JsObject(
      Map(
        "topic" -> JsString(topicName),
        "payload_format" -> JsString("JSON_API_V1"),
        "event_types" -> JsArray(eventTypes.map(JsString(_)).toVector)
      )
    )

    Marshal(entity).to[RequestEntity].flatMap { requestEntity =>
      val request = HttpRequest(
        HttpMethods.POST,
        uri = url,
        headers = List(header),
        entity = requestEntity
      )

      val startTime = System.currentTimeMillis()
      Http().singleRequest(request).map { response =>
        val endTime = System.currentTimeMillis()
        logger.debug(GoogleRequest(HttpMethods.POST.value, url, Option(entity), endTime - startTime, Option(response.status.intValue), None).toJson(GoogleRequestFormat).compactPrint)
        ()
      }
    }
  }

  override def listObjectsWithPrefix(bucketName: GcsBucketName, objectNamePrefix: String): Future[List[GcsObjectName]] = {
    val getter = storage.objects().list(bucketName.value).setPrefix(objectNamePrefix).setMaxResults(maxPageSize)

    val objects = listObjectsRecursive(getter) map { pagesOption =>
      pagesOption.map { pages =>
        pages.flatMap { page =>
          Option(page.getItems.asScala) match {
            case None => List.empty
            case Some(objects) => objects.toList
          }
        }
      }.getOrElse(List.empty)
    }

    // Convert Google StorageObjects to Workbench GcsObjectNames
    objects.map(_.map(obj => GcsObjectName(obj.getName)))
  }

  private def listObjectsRecursive(fetcher: Storage#Objects#List, accumulated: Option[List[Objects]] = Some(Nil)): Future[Option[List[Objects]]] = {
    accumulated match {
      // when accumulated has a Nil list then this must be the first request
      case Some(Nil) => retryWithRecoverWhen500orGoogleError(() => {
        Option(executeGoogleRequest(fetcher))
      }) {
        case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
      }.flatMap(firstPage => listObjectsRecursive(fetcher, firstPage.map(List(_))))

      // the head is the Objects object of the prior request which contains next page token
      case Some(head :: xs) if head.getNextPageToken != null => retryWhen500orGoogleError(() => {
        executeGoogleRequest(fetcher.setPageToken(head.getNextPageToken))
      }).flatMap(nextPage => listObjectsRecursive(fetcher, accumulated.map(pages => nextPage :: pages)))

      // when accumulated is None (bucket does not exist) or next page token is null
      case _ => Future.successful(accumulated)
    }
  }

  override def removeObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Unit] = {
    val remover = storage.objects().delete(bucketName.value, objectName.value)

    retryWithRecoverWhen500orGoogleError { () =>
      executeGoogleRequest(remover)
      ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def setBucketLifecycle(bucketName: GcsBucketName, lifecycleAge: Int, lifecycleType: GcsLifecycleType = Delete): Future[Unit] = {
    val lifecycle = new Lifecycle.Rule().setAction(new Action().setType(lifecycleType.value)).setCondition(new Condition().setAge(lifecycleAge))
    val bucket = new Bucket().setName(bucketName.value).setLifecycle(new Lifecycle().setRule(List(lifecycle).asJava))
    val updater = storage.buckets().update(bucketName.value, bucket)

    retryWhen500orGoogleError(() => {
      executeGoogleRequest(updater)
    })
  }

  override def setBucketAccessControl(bucketName: GcsBucketName, accessControl: GcsAccessControl): Future[Unit] = {
    val entity: String = s"user-${accessControl.email.value}"
    val acl = new BucketAccessControl().setEntity(entity).setRole(accessControl.permission.value)
    val inserter = storage.bucketAccessControls().insert(bucketName.value, acl)

    retryWhen500orGoogleError(() => executeGoogleRequest(inserter)).void
  }

  override def removeBucketAccessControl(bucketName: GcsBucketName, email: WorkbenchEmail): Future[Unit] = {
    val entity: String = s"user-${email.value}"
    val deleter = storage.bucketAccessControls().delete(bucketName.value, entity)

    retryWithRecoverWhen500orGoogleError { () =>
      executeGoogleRequest(deleter)
      ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def setObjectAccessControl(bucketName: GcsBucketName, objectName: GcsObjectName, accessControl: GcsAccessControl): Future[Unit] = {
    val entity: String = s"user-${accessControl.email.value}"
    val acl = new ObjectAccessControl().setEntity(entity).setRole(accessControl.permission.value)
    val inserter = storage.objectAccessControls().insert(bucketName.value, objectName.value, acl)

    retryWhen500orGoogleError(() => executeGoogleRequest(inserter)).void
  }

  override def removeObjectAccessControl(bucketName: GcsBucketName, objectName: GcsObjectName, email: WorkbenchEmail): Future[Unit] = {
    val entity: String = s"user-${email.value}"
    val deleter = storage.objectAccessControls().delete(bucketName.value, objectName.value, entity)

    retryWithRecoverWhen500orGoogleError { () =>
      executeGoogleRequest(deleter)
      ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def setDefaultObjectAccessControl(bucketName: GcsBucketName, accessControl: GcsAccessControl): Future[Unit] = {
    val entity: String = s"user-${accessControl.email.value}"
    val acl = new ObjectAccessControl().setEntity(entity).setRole(accessControl.permission.value)
    val inserter = storage.defaultObjectAccessControls().insert(bucketName.value, acl)

    retryWhen500orGoogleError(() => executeGoogleRequest(inserter)).void
  }

  override def removeDefaultObjectAccessControl(bucketName: GcsBucketName, email: WorkbenchEmail): Future[Unit] = {
    val entity: String = s"user-${email.value}"
    val deleter = storage.defaultObjectAccessControls().delete(bucketName.value, entity)

    retryWithRecoverWhen500orGoogleError { () =>
      executeGoogleRequest(deleter)
      ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

}
