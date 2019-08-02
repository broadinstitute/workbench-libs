package org.broadinstitute.dsde.workbench.google

import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Collections

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import cats.data.OptionT
import cats.instances.future._
import cats.instances.list._
import cats.instances.map._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.semigroup._
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import com.google.api.services.cloudresourcemanager.CloudResourceManager
import com.google.api.services.cloudresourcemanager.model.{Binding => ProjectBinding, Policy => ProjectPolicy, SetIamPolicyRequest => ProjectSetIamPolicyRequest, TestIamPermissionsRequest}
import com.google.api.services.iam.v1.model.{CreateServiceAccountKeyRequest, CreateServiceAccountRequest, ServiceAccount, Binding => ServiceAccountBinding, Policy => ServiceAccountPolicy, ServiceAccountKey => GoogleServiceAccountKey, SetIamPolicyRequest => ServiceAccountSetIamPolicyRequest}
import com.google.api.services.iam.v1.{Iam, IamScopes}
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GoogleUtilities.RetryPredicates._
import org.broadinstitute.dsde.workbench.google.HttpGoogleIamDAO._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.model.google._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Created by rtitle on 10/2/17.
  */
class HttpGoogleIamDAO(appName: String,
                       googleCredentialMode: GoogleCredentialMode,
                       workbenchMetricBaseName: String)
                      (implicit system: ActorSystem, executionContext: ExecutionContext)
  extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName) with GoogleIamDAO {

  @deprecated(message = "This way of instantiating HttpGoogleIamDAO has been deprecated. Please update to use the primary constructor.", since = "0.15")
  def this(serviceAccountClientId: String,
           pemFile: String,
           appName: String,
           workbenchMetricBaseName: String)
          (implicit system: ActorSystem, executionContext: ExecutionContext) = {
    this(appName, Pem(WorkbenchEmail(serviceAccountClientId), new File(pemFile)), workbenchMetricBaseName)
  }

  @deprecated(message = "This way of instantiating HttpGoogleIamDAO has been deprecated. Please update to use the primary constructor.", since = "0.15")
  def this(clientSecrets: GoogleClientSecrets,
           pemFile: String,
           appName: String,
           workbenchMetricBaseName: String)
          (implicit system: ActorSystem, executionContext: ExecutionContext) = {
    this(appName, Pem(WorkbenchEmail(clientSecrets.getDetails.get("client_email").toString), new File(pemFile)), workbenchMetricBaseName)
  }

  override val scopes = List(IamScopes.CLOUD_PLATFORM)

  lazy val cloudResourceManager = new CloudResourceManager.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()

  override implicit val service = GoogleInstrumentedService.Iam

  private lazy val iam = {
    new Iam.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  override def findServiceAccount(serviceAccountProject: GoogleProject, serviceAccountName: ServiceAccountName): Future[Option[google.ServiceAccount]] = {
    findServiceAccount(serviceAccountProject, toServiceAccountEmail(serviceAccountProject, serviceAccountName))
  }

  override def findServiceAccount(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail) = {
    val name = s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}"
    val getter = iam.projects().serviceAccounts().get(name)

    //Return a Future[Option[ServiceAccount]]. The future fails if we get a Google error we don't understand. The Option is None if we get a 404, i.e. the SA doesn't exist.
    val findOption = OptionT(retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      Option(executeGoogleRequest(getter))
    } {
      case t: GoogleJsonResponseException if t.getStatusCode == 404 =>
        None
      case t: GoogleJsonResponseException if t.getStatusCode == 403 && t.getDetails.getErrors.asScala.head.getMessage.equalsIgnoreCase("Unable to extract resource containers.") =>
        // added to catch and fix a google issue that popped up https://console.cloud.google.com/support/cases/detail/17978989?project=broad-dsde-prod
        None
    })

    //Turn it into a Workbench SA type.
    (findOption map { serviceAccount =>
      google.ServiceAccount(
        ServiceAccountSubjectId(serviceAccount.getUniqueId),
        WorkbenchEmail(serviceAccount.getEmail),
        ServiceAccountDisplayName(serviceAccount.getDisplayName))
    }).value
  }

  override def createServiceAccount(serviceAccountProject: GoogleProject, serviceAccountName: ServiceAccountName, displayName: ServiceAccountDisplayName): Future[google.ServiceAccount] = {
    val request = new CreateServiceAccountRequest().setAccountId(serviceAccountName.value)
      .setServiceAccount(new ServiceAccount().setDisplayName(displayName.value))
    val inserter = iam.projects().serviceAccounts().create(s"projects/${serviceAccountProject.value}", request)
    retryWithRecover(when5xx, whenUsageLimited, whenGlobalUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(inserter)
    } {
      case t: GoogleJsonResponseException if t.getStatusCode == StatusCodes.NotFound.intValue => throw new WorkbenchException(s"The project [${serviceAccountProject.value}] was not found")
    } map { serviceAccount =>
      google.ServiceAccount(ServiceAccountSubjectId(serviceAccount.getUniqueId), WorkbenchEmail(serviceAccount.getEmail), ServiceAccountDisplayName(serviceAccount.getDisplayName))
    }
  }

  override def removeServiceAccount(serviceAccountProject: GoogleProject, serviceAccountName: ServiceAccountName): Future[Unit] = {
    val serviceAccountEmail = toServiceAccountEmail(serviceAccountProject, serviceAccountName)
    val name = s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}"
    val deleter = iam.projects().serviceAccounts().delete(name)
    retryWithRecover(when5xx, whenUsageLimited, whenGlobalUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(deleter)
      ()
    } {
      // if the service account is already gone, don't fail
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def testIamPermission(project: GoogleProject, iamPermissions: Set[IamPermission]): Future[Set[IamPermission]] = {
    val testRequest = new TestIamPermissionsRequest().setPermissions(iamPermissions.map(p => p.value).toList.asJava)
    val request = cloudResourceManager.projects().testIamPermissions(project.value, testRequest)
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(request)
    } map { response =>
      Option(response.getPermissions).getOrElse(Collections.emptyList()).asScala.toSet.map(IamPermission)
    }
  }

  override def addIamRolesForUser(iamProject: GoogleProject, userEmail: WorkbenchEmail, rolesToAdd: Set[String]): Future[Unit] = {
    // Note the project here is the one in which we're adding the IAM roles
    getProjectPolicy(iamProject).flatMap { policy =>
      val updatedPolicy = updatePolicy(policy, userEmail, rolesToAdd, Set.empty)
      val policyRequest = new ProjectSetIamPolicyRequest().setPolicy(updatedPolicy).setUpdateMask("bindings,etag")
      val request = cloudResourceManager.projects().setIamPolicy(iamProject.value, policyRequest)
      retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
        executeGoogleRequest(request)
      }.void
    }
  }

  override def removeIamRolesForUser(iamProject: GoogleProject, userEmail: WorkbenchEmail, rolesToRemove: Set[String]): Future[Unit] = {
    // Note the project here is the one in which we're removing the IAM roles
    getProjectPolicy(iamProject).flatMap { policy =>
      val updatedPolicy = updatePolicy(policy, userEmail, Set.empty, rolesToRemove)
      val policyRequest = new ProjectSetIamPolicyRequest().setPolicy(updatedPolicy).setUpdateMask("bindings,etag")
      val request = cloudResourceManager.projects().setIamPolicy(iamProject.value, policyRequest)
      retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
        executeGoogleRequest(request)
      }.void
    }
  }

  override def addServiceAccountUserRoleForUser(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail, userEmail: WorkbenchEmail): Future[Unit] = {
    // Note the project here is the one in which we're adding the IAM roles.
    // In this case the serviceAccountEmail acts as a resource, not an identity. Therefore the serviceAccountEmail
    // should live in the provided serviceAccountProject. For more information on service account permissions, see:
    // - https://cloud.google.com/iam/docs/service-accounts#service_account_permissions
    // - https://cloud.google.com/iam/docs/service-accounts#the_service_account_user_role
    getServiceAccountPolicy(serviceAccountProject, serviceAccountEmail).flatMap { policy =>
      val updatedPolicy = updatePolicy(policy, userEmail, Set("roles/iam.serviceAccountUser"), Set.empty)
      val policyRequest = new ServiceAccountSetIamPolicyRequest().setPolicy(updatedPolicy)
      val request = iam.projects().serviceAccounts().setIamPolicy(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}", policyRequest)
      retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
        executeGoogleRequest(request)
      }.void
    }
  }

  override def createServiceAccountKey(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail): Future[ServiceAccountKey] = {
    val request = new CreateServiceAccountKeyRequest()
      .setPrivateKeyType("TYPE_GOOGLE_CREDENTIALS_FILE")
      .setKeyAlgorithm("KEY_ALG_RSA_2048")
    val creater = iam.projects().serviceAccounts().keys().create(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}", request)
    retry(when5xx, whenUsageLimited, whenGlobalUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(creater)
    } map googleKeyToWorkbenchKey
  }

  override def removeServiceAccountKey(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail, keyId: ServiceAccountKeyId): Future[Unit] = {
    val request = iam.projects().serviceAccounts().keys().delete(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}/keys/${keyId.value}")
    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException){ () =>
      executeGoogleRequest(request)
      ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def listServiceAccountKeys(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail): Future[Seq[ServiceAccountKey]] = {
    val request = iam.projects().serviceAccounts().keys().list(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}")

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(request)
    } map { response =>
      Option(response.getKeys).getOrElse(Collections.emptyList()).asScala map googleKeyToWorkbenchKey
    }
  }

  override def listUserManagedServiceAccountKeys(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail): Future[Seq[ServiceAccountKey]] = {
    val request = iam.projects().serviceAccounts().keys().list(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}").setKeyTypes(List("USER_MANAGED").asJava)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(request)
    } map { response =>
      Option(response.getKeys).getOrElse(Collections.emptyList()).asScala map googleKeyToWorkbenchKey
    }
  }

  private def googleKeyToWorkbenchKey(googleKey: GoogleServiceAccountKey): ServiceAccountKey = {
    ServiceAccountKey(
      ServiceAccountKeyId(googleKey.getName.split('/').last),
      ServiceAccountPrivateKeyData(googleKey.getPrivateKeyData),
      Option(googleKey.getValidAfterTime).flatMap(googleTimestampToInstant),
      Option(googleKey.getValidBeforeTime).flatMap(googleTimestampToInstant))
  }

  private def googleTimestampToInstant(googleTimestamp: String): Option[Instant] = {
    Try {
      Instant.from(DateTimeFormatter.ISO_INSTANT.parse(googleTimestamp))
    }.toOption
  }

  private def getProjectPolicy(googleProject: GoogleProject): Future[Policy] = {
    val request = cloudResourceManager.projects().getIamPolicy(googleProject.value, null)
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(request)
    }
  }

  private def getServiceAccountPolicy(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail): Future[Policy] = {
    val request = iam.projects().serviceAccounts().getIamPolicy(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}")
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(request)
    }
  }

  /**
    * Read-modify-write a Policy to insert or remove new bindings for the given member and roles.
    * Note that if the same role is in both rolesToAdd and rolesToRemove, the deletion takes precedence.
    */
  private def updatePolicy(policy: Policy, userEmail: WorkbenchEmail, rolesToAdd: Set[String], rolesToRemove: Set[String]): Policy = {
    val memberType = if (isServiceAccount(userEmail)) "serviceAccount" else "user"
    val email = s"$memberType:${userEmail.value}"

    // current members grouped by role
    val curMembersByRole: Map[String, List[String]] = policy.bindings.foldMap { binding =>
      Map(binding.role -> binding.members)
    }

    // Apply additions
    val withAdditions = if (rolesToAdd.nonEmpty) {
      val rolesToAddMap = rolesToAdd.map { _ -> List(email) }.toMap
      curMembersByRole |+| rolesToAddMap
    } else {
      curMembersByRole
    }

    // Apply deletions
    val newMembersByRole = if (rolesToRemove.nonEmpty) {
      withAdditions.toList.foldMap { case (role, members) =>
        if (rolesToRemove.contains(role)) {
          val filtered = members.filterNot(_ == email)
          if (filtered.isEmpty) Map.empty[String, List[String]]
          else Map(role -> filtered)
        } else {
          Map(role -> members)
        }
      }
    } else {
      withAdditions
    }

    val bindings = newMembersByRole.map { case (role, members) =>
      Binding(role, members)
    }.toList

    Policy(bindings, policy.etag)
  }
}

object HttpGoogleIamDAO {
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

  private case class Binding(role: String, members: List[String])
  private case class Policy(bindings: List[Binding], etag: String)

  private implicit def fromProjectBinding(projectBinding: ProjectBinding): Binding = {
    Binding(projectBinding.getRole, projectBinding.getMembers)
  }

  private implicit def fromServiceAccountBinding(serviceAccountBinding: ServiceAccountBinding): Binding = {
    Binding(serviceAccountBinding.getRole, serviceAccountBinding.getMembers)
  }

  private implicit def fromProjectPolicy(projectPolicy: ProjectPolicy): Policy = {
    Policy(projectPolicy.getBindings.map(fromProjectBinding), projectPolicy.getEtag)
  }

  private implicit def fromServiceAccountPolicy(serviceAccountPolicy: ServiceAccountPolicy): Policy = {
    Policy(serviceAccountPolicy.getBindings.map(fromServiceAccountBinding), serviceAccountPolicy.getEtag)
  }

  private implicit def toServiceAccountPolicy(policy: Policy): ServiceAccountPolicy = {
    new ServiceAccountPolicy().setBindings(policy.bindings.map { b =>
      new ServiceAccountBinding().setRole(b.role).setMembers(b.members.asJava)
    }.asJava).setEtag(policy.etag)
  }

  private implicit def toProjectPolicy(policy: Policy): ProjectPolicy = {
    new ProjectPolicy().setBindings(policy.bindings.map { b =>
      new ProjectBinding().setRole(b.role).setMembers(b.members.asJava)
    }.asJava).setEtag(policy.etag)
  }

  private implicit def nullSafeList[A](list: java.util.List[A]): List[A] = {
    Option(list).map(_.asScala.toList).getOrElse(List.empty[A])
  }
}
