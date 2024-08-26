package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
import java.time.Instant
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model._
import cats.syntax.all._
import com.google.api.client.http.{AbstractInputStreamContent, FileContent, HttpResponseException, InputStreamContent}
import com.google.api.services.storage.model.Bucket.Lifecycle
import com.google.api.services.storage.model.Bucket.Lifecycle.Rule.{Action, Condition}
import com.google.api.services.storage.model.Policy.{Bindings => BucketBinding}
import com.google.api.services.storage.model.{
  Bucket,
  BucketAccessControl,
  BucketAccessControls,
  Expr => BucketExpr,
  ObjectAccessControl,
  ObjectAccessControls,
  Objects,
  Policy => BucketPolicy,
  StorageObject
}
import com.google.api.services.storage.{Storage, StorageScopes}
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GoogleUtilities.RetryPredicates._
import org.broadinstitute.dsde.workbench.google.IamOperations.{policyVersion, updatePolicy}
import org.broadinstitute.dsde.workbench.google.HttpGoogleStorageDAO._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.GcsLifecycleTypes.{Delete, GcsLifecycleType}
import org.broadinstitute.dsde.workbench.model.google.GcsRoles.{GcsRole, Owner, Reader}
import org.broadinstitute.dsde.workbench.model.google.iam.IamMemberTypes.IamMemberType
import org.broadinstitute.dsde.workbench.model.google._
import org.broadinstitute.dsde.workbench.model.google.iam.{Binding, Expr, Policy}

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

class HttpGoogleStorageDAO(appName: String,
                           googleCredentialMode: GoogleCredentialMode,
                           workbenchMetricBaseName: String,
                           maxPageSize: Long = 1000
)(implicit system: ActorSystem, executionContext: ExecutionContext)
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
           maxPageSize: Long
  )(implicit system: ActorSystem, executionContext: ExecutionContext) =
    this(appName, Pem(WorkbenchEmail(serviceAccountClientId), new File(pemFile)), workbenchMetricBaseName, maxPageSize)
  override val scopes = Seq(
    StorageScopes.DEVSTORAGE_FULL_CONTROL,
    "https://www.googleapis.com/auth/cloud-platform",
    "https://www.googleapis.com/auth/userinfo.email",
    "https://www.googleapis.com/auth/userinfo.profile"
  )

  implicit override val service: GoogleInstrumentedService.Value = GoogleInstrumentedService.Storage

  private lazy val storage =
    new Storage.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()

  override def createBucket(billingProject: GoogleProject,
                            bucketName: GcsBucketName,
                            readers: List[GcsEntity] = List.empty,
                            owners: List[GcsEntity] = List.empty
  ): Future[GcsBucketName] = {
    val bucketAcl =
      readers.map(entity => new BucketAccessControl().setEntity(entity.toString).setRole(Reader.value)) ++ owners
        .map(entity => new BucketAccessControl().setEntity(entity.toString).setRole(Owner.value))
    val defaultBucketObjectAcl = readers.map(entity =>
      new ObjectAccessControl().setEntity(entity.toString).setRole(Reader.value)
    ) ++ owners.map(entity => new ObjectAccessControl().setEntity(entity.toString).setRole(Owner.value))
    val bucket = new Bucket()
      .setName(bucketName.value)
      .setAcl(bucketAcl.asJava)
      .setDefaultObjectAcl(defaultBucketObjectAcl.asJava)

    val inserter = storage.buckets().insert(billingProject.value, bucket)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(inserter)
      bucketName
    }
  }

  override def getBucket(bucketName: GcsBucketName): Future[Bucket] =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(storage.buckets().get(bucketName.value))
    }

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
                           objectType: String
  ): Future[Unit] =
    storeObject(bucketName, objectName, new InputStreamContent(objectType, objectContents))

  override def storeObject(bucketName: GcsBucketName,
                           objectName: GcsObjectName,
                           objectContents: File,
                           objectType: String
  ): Future[Unit] =
    storeObject(bucketName, objectName, new FileContent(objectType, objectContents))

  private def storeObject(bucketName: GcsBucketName,
                          objectName: GcsObjectName,
                          content: AbstractInputStreamContent
  ): Future[Unit] = {
    val storageObject = new StorageObject().setName(objectName.value)
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      val inserter = storage.objects().insert(bucketName.value, storageObject, content)
      inserter.getMediaHttpUploader.setDirectUploadEnabled(true)

      executeGoogleRequest(inserter)
    }
  }

  override def getObject(bucketName: GcsBucketName, objectName: GcsObjectName): Future[Option[ByteArrayOutputStream]] =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
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
    }

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

  // This functionality doesn't exist in the com.google.apis Java library.
  // When we migrate to the com.google.cloud library, we will be able to re-write this to use their implementation
  override def setObjectChangePubSubTrigger(bucketName: GcsBucketName,
                                            topicName: String,
                                            eventTypes: List[String]
  ): Future[Unit] = {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import org.broadinstitute.dsde.workbench.google.GoogleRequestJsonSupport._
    import spray.json._

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
        val googleRequest = GoogleRequest(HttpMethods.POST.value,
                                          url,
                                          Option(entity),
                                          endTime - startTime,
                                          Option(response.status.intValue),
                                          None
        )
        logGoogleRequest(googleRequest)
        ()
      }
    }
  }

  override def listObjectsWithPrefix(bucketName: GcsBucketName,
                                     objectNamePrefix: String
  ): Future[List[GcsObjectName]] = {
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
                                   accumulated: Option[List[Objects]] = Some(Nil)
  ): Future[Option[List[Objects]]] =
    accumulated match {
      // when accumulated has a Nil list then this must be the first request
      case Some(Nil) =>
        retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
          () =>
            Option(executeGoogleRequest(fetcher))
        } {
          case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => None
        }.flatMap(firstPage => listObjectsRecursive(fetcher, firstPage.map(List(_))))

      // the head is the Objects object of the prior request which contains next page token
      case Some(head :: _) if head.getNextPageToken != null =>
        retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
          executeGoogleRequest(fetcher.setPageToken(head.getNextPageToken))
        }.flatMap(nextPage => listObjectsRecursive(fetcher, accumulated.map(pages => nextPage :: pages)))

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
                                  lifecycleType: GcsLifecycleType = Delete
  ): Future[Unit] = {
    val lifecycle = new Lifecycle.Rule()
      .setAction(new Action().setType(lifecycleType.value))
      .setCondition(new Condition().setAge(lifecycleAge))
    val bucket = new Bucket().setName(bucketName.value).setLifecycle(new Lifecycle().setRule(List(lifecycle).asJava))
    val updater = storage.buckets().update(bucketName.value, bucket)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(updater)
    }
  }

  override def copyObject(srcBucketName: GcsBucketName,
                          srcObjectName: GcsObjectName,
                          destBucketName: GcsBucketName,
                          destObjectName: GcsObjectName
  ): Future[Unit] = {
    val copier = storage
      .objects()
      .copy(srcBucketName.value, srcObjectName.value, destBucketName.value, destObjectName.value, new StorageObject())
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(copier)
    }
  }

  override def setBucketAccessControl(bucketName: GcsBucketName, entity: GcsEntity, role: GcsRole): Future[Unit] = {
    val acl = new BucketAccessControl().setEntity(entity.toString).setRole(role.value)
    val inserter = storage.bucketAccessControls().insert(bucketName.value, acl)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() =>
      executeGoogleRequest(inserter)
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
                                      role: GcsRole
  ): Future[Unit] = {
    val acl = new ObjectAccessControl().setEntity(entity.toString).setRole(role.value)
    val inserter = storage.objectAccessControls().insert(bucketName.value, objectName.value, acl)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() =>
      executeGoogleRequest(inserter)
    ).void
  }

  override def removeObjectAccessControl(bucketName: GcsBucketName,
                                         objectName: GcsObjectName,
                                         entity: GcsEntity
  ): Future[Unit] = {
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
                                             role: GcsRole
  ): Future[Unit] = {
    val acl = new ObjectAccessControl().setEntity(entity.toString).setRole(role.value)
    val inserter = storage.defaultObjectAccessControls().insert(bucketName.value, acl)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException)(() =>
      executeGoogleRequest(inserter)
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
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(storage.bucketAccessControls().list(bucketName.value))
    }

  override def getDefaultObjectAccessControls(bucketName: GcsBucketName): Future[ObjectAccessControls] =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(storage.defaultObjectAccessControls().list(bucketName.value))
    }

  override def setRequesterPays(bucketName: GcsBucketName, requesterPays: Boolean): Future[Unit] =
    getBucket(bucketName).map { bucket =>
      // if the billing object is null or the current requester pays setting not the same as the requested setting,
      //   change requester pays setting to the requested setting.
      // else do nothing
      if (bucket.getBilling == null || bucket.getBilling.getRequesterPays != requesterPays) {
        val rp = new Bucket.Billing().setRequesterPays(requesterPays)
        retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
          executeGoogleRequest(storage.buckets().patch(bucketName.value, bucket.setBilling(rp)))
        }
      }
    }

  override def addIamRoles(bucketName: GcsBucketName,
                           userEmail: WorkbenchEmail,
                           memberType: IamMemberType,
                           rolesToAdd: Set[String],
                           retryIfGroupDoesNotExist: Boolean = false,
                           condition: Option[Expr] = None,
                           userProject: Option[GoogleProject]
  ): Future[Boolean] =
    modifyIamRoles(bucketName,
                   userEmail,
                   memberType,
                   rolesToAdd,
                   Set.empty,
                   retryIfGroupDoesNotExist,
                   condition,
                   userProject
    )

  override def removeIamRoles(bucketName: GcsBucketName,
                              userEmail: WorkbenchEmail,
                              memberType: IamMemberType,
                              rolesToRemove: Set[String],
                              retryIfGroupDoesNotExist: Boolean = false,
                              userProject: Option[GoogleProject]
  ): Future[Boolean] =
    modifyIamRoles(bucketName,
                   userEmail,
                   memberType,
                   Set.empty,
                   rolesToRemove,
                   retryIfGroupDoesNotExist,
                   None,
                   userProject
    )

  override def getBucketPolicy(bucketName: GcsBucketName, userProject: Option[GoogleProject]): Future[BucketPolicy] =
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException, when409) { () =>
      val request = storage.buckets().getIamPolicy(bucketName.value).setOptionsRequestedPolicyVersion(policyVersion)
      executeGoogleRequest(userProject.map(p => request.setUserProject(p.value)).getOrElse(request))
    }

  private def modifyIamRoles(bucketName: GcsBucketName,
                             userEmail: WorkbenchEmail,
                             memberType: IamMemberType,
                             rolesToAdd: Set[String],
                             rolesToRemove: Set[String],
                             retryIfGroupDoesNotExist: Boolean,
                             condition: Option[Expr],
                             userProject: Option[GoogleProject]
  ): Future[Boolean] = {
    // Note the project here is the one in which we're removing the IAM roles
    // Retry 409s here as recommended for concurrent modifications of the IAM policy

    val basePredicateList: Seq[Throwable => Boolean] = Seq(when5xx,
                                                           whenUsageLimited,
                                                           when404,
                                                           whenInvalidValueOnBucketCreation,
                                                           whenNonHttpIOException,
                                                           when409,
                                                           when412
    )
    val finalPredicateList: Seq[Throwable => Boolean] =
      basePredicateList ++ (if (retryIfGroupDoesNotExist) Seq(whenGroupDoesNotExist: Throwable => Boolean)
                            else Nil)

    retry(
      finalPredicateList: _*
    ) { () =>
      updateIamPolicy(bucketName, userEmail, memberType, rolesToAdd, rolesToRemove, condition, userProject)
    }
  }
  private def updateIamPolicy(bucketName: GcsBucketName,
                              userEmail: WorkbenchEmail,
                              memberType: IamMemberType,
                              rolesToAdd: Set[String],
                              rolesToRemove: Set[String],
                              condition: Option[Expr],
                              userProject: Option[GoogleProject]
  ): Boolean = {
    // It is important that we call getIamPolicy within the same retry block as we call setIamPolicy
    // getIamPolicy gets the etag that is used in setIamPolicy, the etag is used to detect concurrent
    // modifications and if that happens we need to be sure to get a new etag before retrying setIamPolicy
    val existingPolicyRequest =
      storage.buckets().getIamPolicy(bucketName.value).setOptionsRequestedPolicyVersion(policyVersion)
    val existingPolicy = executeGoogleRequest(
      userProject.map(p => existingPolicyRequest.setUserProject(p.value)).getOrElse(existingPolicyRequest)
    )
    val updatedPolicy = updatePolicy(existingPolicy, userEmail, memberType, rolesToAdd, rolesToRemove, condition)

    // Policy objects use Sets so are not sensitive to ordering and duplication
    if (existingPolicy == updatedPolicy) {
      false
    } else {
      val request = storage.buckets().setIamPolicy(bucketName.value, updatedPolicy)
      executeGoogleRequest(userProject.map(project => request.setUserProject(project.value)).getOrElse(request))
      true
    }
  }
}

object HttpGoogleStorageDAO {
  import scala.language.implicitConversions

  /*
   * Google has different model classes for policy manipulation depending on the type of resource.
   *
   * For project-level policies we have:
   *   com.google.api.services.cloudresourcemanager.model.{Policy, Binding}
   *
   * For service account-level policies we have:
   *   com.google.api.services.iam.v1.model.{Policy, Binding}
   *
   * These classes are for all intents and purposes identical. To deal with this we create our own
   * {Policy, Binding} case classes in Scala, with implicit conversions to/from the above Google classes.
   */

  implicit private def fromBucketExpr(bucketExpr: BucketExpr): Expr =
    if (bucketExpr == null) {
      null
    } else {
      Expr(bucketExpr.getDescription, bucketExpr.getExpression, bucketExpr.getLocation, bucketExpr.getTitle)
    }

  implicit def toBucketExpr(expr: Expr): BucketExpr =
    if (expr == null) {
      null
    } else {
      new BucketExpr()
        .setDescription(expr.description)
        .setExpression(expr.expression)
        .setLocation(expr.location)
        .setTitle(expr.title)
    }
  implicit private def fromBucketBinding(bucketBinding: BucketBinding): Binding =
    iam.Binding(bucketBinding.getRole, bucketBinding.getMembers.toSet, bucketBinding.getCondition)

  implicit def fromBucketPolicy(bucketPolicy: BucketPolicy): Policy =
    Policy(bucketPolicy.getBindings.map(fromBucketBinding).toSet, bucketPolicy.getEtag)

  implicit def toBucketPolicy(policy: Policy): BucketPolicy =
    new BucketPolicy()
      .setBindings(
        policy.bindings
          .map { b =>
            new BucketBinding().setRole(b.role).setMembers(b.members.toList.asJava).setCondition(b.condition)
          }
          .toList
          .asJava
      )
      .setEtag(policy.etag)
      .setVersion(policyVersion)

  implicit private def nullSafeList[A](list: java.util.List[A]): List[A] =
    Option(list).map(_.asScala.toList).getOrElse(List.empty[A])
}
