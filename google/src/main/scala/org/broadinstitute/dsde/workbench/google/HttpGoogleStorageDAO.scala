package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{StatusCodes, _}
import akka.stream.ActorMaterializer
import cats.implicits._
import com.google.api.client.http.{AbstractInputStreamContent, FileContent, HttpResponseException, InputStreamContent}
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.plus.PlusScopes
import com.google.api.services.storage.model.Bucket.Lifecycle
import com.google.api.services.storage.model.Bucket.Lifecycle.Rule.{Action, Condition}
import com.google.api.services.storage.model._
import com.google.api.services.storage.{Storage, StorageScopes}
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GoogleUtilities.RetryPredicates._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.GcsLifecycleTypes.{Delete, GcsLifecycleType}
import org.broadinstitute.dsde.workbench.model.google.GcsRoles.{GcsRole, Owner, Reader}
import org.broadinstitute.dsde.workbench.model.google._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class HttpGoogleStorageDAO(appName: String,
                           googleCredentialMode: GoogleCredentialMode,
                           workbenchMetricBaseName: String,
                           maxPageSize: Long = 1000)(implicit system: ActorSystem, executionContext: ExecutionContext)
    extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName)
    with GoogleStorageDAO {

  @deprecated(
    message =
      "This way of instantiating HttpGoogleStorageDAO has been deprecated. Please update to use the primary constructor.",
    since = "0.15"
  )
  def this(serviceAccountClientId: String,
           pemFile: String,
           appName: String,
           workbenchMetricBaseName: String,
           maxPageSize: Long)(implicit system: ActorSystem, executionContext: ExecutionContext) = {
    this(appName, Pem(WorkbenchEmail(serviceAccountClientId), new File(pemFile)), workbenchMetricBaseName, maxPageSize)
  }
  override val scopes = Seq(StorageScopes.DEVSTORAGE_FULL_CONTROL,
                            ComputeScopes.COMPUTE,
                            PlusScopes.USERINFO_EMAIL,
                            PlusScopes.USERINFO_PROFILE)

  implicit override val service = GoogleInstrumentedService.Storage

  private lazy val storage = {
    new Storage.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  override def createBucket(billingProject: GoogleProject,
                            bucketName: GcsBucketName,
                            readers: List[GcsEntity] = List.empty,
                            owners: List[GcsEntity] = List.empty): Future[GcsBucketName] = {
    val bucketAcl = readers.map(entity => new BucketAccessControl().setEntity(entity.toString).setRole(Reader.value)) ++ owners
      .map(entity => new BucketAccessControl().setEntity(entity.toString).setRole(Owner.value))
    val defaultBucketObjectAcl = readers.map(
      entity => new ObjectAccessControl().setEntity(entity.toString).setRole(Reader.value)
    ) ++ owners.map(entity => new ObjectAccessControl().setEntity(entity.toString).setRole(Owner.value))
    val bucket = new Bucket()
      .setName(bucketName.value)
      .setAcl(bucketAcl.asJava)
      .setDefaultObjectAcl(defaultBucketObjectAcl.asJava)

    val inserter = storage.buckets().insert(billingProject.value, bucket)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() => {
      executeGoogleRequest(inserter)
      bucketName
    })
  }

  override def getBucket(bucketName: GcsBucketName): Future[Bucket] =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() => {
      executeGoogleRequest(storage.buckets().get(bucketName.value))
    })

  override def deleteBucket(bucketName: GcsBucketName, recurse: Boolean): Future[Unit] = {
    // If `recurse` is true, first delete all objects in the bucket
    val deleteObjectsFuture = if (recurse) {
      val listObjectsRequest = storage.objects().list(bucketName.value)
      retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
        () =>
          Option(executeGoogleRequest(listObjectsRequest))
      } {
        case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
      }.flatMap { objects =>
        // Handle null responses from Google
        val items = objects.flatMap(objs => Option(objs.getItems)).map(_.asScala).getOrElse(Seq.empty)
        Future.traverse(items) { item =>
          removeObject(bucketName, GcsObjectName(item.getName, Instant.ofEpochMilli(item.getTimeCreated.getValue)))
        }
      }.void
    } else Future.successful(())

    deleteObjectsFuture.flatMap { _ =>
      val deleteBucketRequest = storage.buckets().delete(bucketName.value)
      retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
        () =>
          executeGoogleRequest(deleteBucketRequest)
          ()
      } {
        case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
      }
    }
  }

  override def bucketExists(bucketName: GcsBucketName): Future[Boolean] = {
    val getter = storage.buckets().get(bucketName.value)
    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(getter)
        true
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => false
    }
  }

  override def storeObject(bucketName: GcsBucketName,
                           objectName: GcsObjectName,
                           objectContents: ByteArrayInputStream,
                           objectType: String): Future[Unit] =
    storeObject(bucketName, objectName, new InputStreamContent(objectType, objectContents))

  override def storeObject(bucketName: GcsBucketName,
                           objectName: GcsObjectName,
                           objectContents: File,
                           objectType: String): Future[Unit] =
    storeObject(bucketName, objectName, new FileContent(objectType, objectContents))

  private def storeObject(bucketName: GcsBucketName,
                          objectName: GcsObjectName,
                          content: AbstractInputStreamContent): Future[Unit] = {
    val storageObject = new StorageObject().setName(objectName.value)
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() => {
      val inserter = storage.objects().insert(bucketName.value, storageObject, content)
      inserter.getMediaHttpUploader.setDirectUploadEnabled(true)

      executeGoogleRequest(inserter)
    })
  }

  override def getObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Option[ByteArrayOutputStream]] =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() => {
      try {
        val getter = storage.objects().get(bucketName.value, objectName.value)
        getter.getMediaHttpDownloader.setDirectDownloadEnabled(true)
        val objectBytes = new ByteArrayOutputStream()
        getter.executeMediaAndDownloadTo(objectBytes)
        executeGoogleRequest(getter)
        Option(objectBytes)
      } catch {
        case t: HttpResponseException if t.getStatusCode == StatusCodes.NotFound.intValue => None
      }
    })

  override def objectExists(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Boolean] = {
    val getter = storage.objects().get(bucketName.value, objectName.value)
    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(getter)
        true
    } {
      case t: HttpResponseException if t.getStatusCode == StatusCodes.NotFound.intValue => false
    }
  }

  //This functionality doesn't exist in the com.google.apis Java library.
  //When we migrate to the com.google.cloud library, we will be able to re-write this to use their implementation
  override def setObjectChangePubSubTrigger(bucketName: GcsBucketName,
                                            topicName: String,
                                            eventTypes: List[String]): Future[Unit] = {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import org.broadinstitute.dsde.workbench.google.GoogleRequestJsonSupport._
    import spray.json._
    implicit val materializer = ActorMaterializer()

    googleCredential.refreshToken()

    val url = s"https://www.googleapis.com/storage/v1/b/$bucketName/notificationConfigs"
    val header = headers.Authorization(OAuth2BearerToken(googleCredential.getAccessToken))

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
        logger.debug(
          GoogleRequest(HttpMethods.POST.value,
                        url,
                        Option(entity),
                        endTime - startTime,
                        Option(response.status.intValue),
                        None).toJson(GoogleRequestFormat).compactPrint
        )
        ()
      }
    }
  }

  override def listObjectsWithPrefix(bucketName: GcsBucketName,
                                     objectNamePrefix: String): Future[List[GcsObjectName]] = {
    val getter = storage.objects().list(bucketName.value).setPrefix(objectNamePrefix).setMaxResults(maxPageSize)

    val objects = listObjectsRecursive(getter) map { pagesOption =>
      pagesOption
        .map { pages =>
          pages.flatMap { page =>
            Option(page.getItems) match {
              case None          => List.empty
              case Some(objects) => objects.asScala.toList
            }
          }
        }
        .getOrElse(List.empty)
    }

    // Convert Google StorageObjects to Workbench GcsObjectNames
    objects.map(_.map(obj => GcsObjectName(obj.getName, Instant.ofEpochMilli(obj.getTimeCreated.getValue))))
  }

  private def listObjectsRecursive(fetcher: Storage#Objects#List,
                                   accumulated: Option[List[Objects]] = Some(Nil)): Future[Option[List[Objects]]] =
    accumulated match {
      // when accumulated has a Nil list then this must be the first request
      case Some(Nil) =>
        retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(
          () => {
            Option(executeGoogleRequest(fetcher))
          }
        ) {
          case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
        }.flatMap(firstPage => listObjectsRecursive(fetcher, firstPage.map(List(_))))

      // the head is the Objects object of the prior request which contains next page token
      case Some(head :: _) if head.getNextPageToken != null =>
        retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() => {
          executeGoogleRequest(fetcher.setPageToken(head.getNextPageToken))
        }).flatMap(nextPage => listObjectsRecursive(fetcher, accumulated.map(pages => nextPage :: pages)))

      // when accumulated is None (bucket does not exist) or next page token is null
      case _ => Future.successful(accumulated)
    }

  override def removeObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Unit] = {
    val remover = storage.objects().delete(bucketName.value, objectName.value)

    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(remover)
        ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def setBucketLifecycle(bucketName: GcsBucketName,
                                  lifecycleAge: Int,
                                  lifecycleType: GcsLifecycleType = Delete): Future[Unit] = {
    val lifecycle = new Lifecycle.Rule()
      .setAction(new Action().setType(lifecycleType.value))
      .setCondition(new Condition().setAge(lifecycleAge))
    val bucket = new Bucket().setName(bucketName.value).setLifecycle(new Lifecycle().setRule(List(lifecycle).asJava))
    val updater = storage.buckets().update(bucketName.value, bucket)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() => {
      executeGoogleRequest(updater)
    })
  }

  override def copyObject(srcBucketName: GcsBucketName,
                          srcObjectName: GcsObjectName,
                          destBucketName: GcsBucketName,
                          destObjectName: GcsObjectName): Future[Unit] = {
    val copier = storage
      .objects()
      .copy(srcBucketName.value, srcObjectName.value, destBucketName.value, destObjectName.value, new StorageObject())
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() => {
      executeGoogleRequest(copier)
    })
  }

  override def setBucketAccessControl(bucketName: GcsBucketName, entity: GcsEntity, role: GcsRole): Future[Unit] = {
    val acl = new BucketAccessControl().setEntity(entity.toString).setRole(role.value)
    val inserter = storage.bucketAccessControls().insert(bucketName.value, acl)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(
      () => executeGoogleRequest(inserter)
    ).void
  }

  override def removeBucketAccessControl(bucketName: GcsBucketName, entity: GcsEntity): Future[Unit] = {
    val deleter = storage.bucketAccessControls().delete(bucketName.value, entity.toString)

    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(deleter)
        ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def setObjectAccessControl(bucketName: GcsBucketName,
                                      objectName: GcsObjectName,
                                      entity: GcsEntity,
                                      role: GcsRole): Future[Unit] = {
    val acl = new ObjectAccessControl().setEntity(entity.toString).setRole(role.value)
    val inserter = storage.objectAccessControls().insert(bucketName.value, objectName.value, acl)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(
      () => executeGoogleRequest(inserter)
    ).void
  }

  override def removeObjectAccessControl(bucketName: GcsBucketName,
                                         objectName: GcsObjectName,
                                         entity: GcsEntity): Future[Unit] = {
    val deleter = storage.objectAccessControls().delete(bucketName.value, objectName.value, entity.toString)

    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(deleter)
        ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def setDefaultObjectAccessControl(bucketName: GcsBucketName,
                                             entity: GcsEntity,
                                             role: GcsRole): Future[Unit] = {
    val acl = new ObjectAccessControl().setEntity(entity.toString).setRole(role.value)
    val inserter = storage.defaultObjectAccessControls().insert(bucketName.value, acl)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(
      () => executeGoogleRequest(inserter)
    ).void
  }

  override def removeDefaultObjectAccessControl(bucketName: GcsBucketName, entity: GcsEntity): Future[Unit] = {
    val deleter = storage.defaultObjectAccessControls().delete(bucketName.value, entity.toString)

    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(deleter)
        ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def getBucketAccessControls(bucketName: GcsBucketName): Future[BucketAccessControls] =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() => {
      executeGoogleRequest(storage.bucketAccessControls().list(bucketName.value))
    })

  override def getDefaultObjectAccessControls(bucketName: GcsBucketName): Future[ObjectAccessControls] =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() => {
      executeGoogleRequest(storage.defaultObjectAccessControls().list(bucketName.value))
    })
}
