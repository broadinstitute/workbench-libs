package org.broadinstitute.dsde.workbench.google

import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Collections
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import cats.data.OptionT
import cats.instances.future._
import cats.syntax.functor._
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import com.google.api.services.cloudresourcemanager.CloudResourceManager
import com.google.api.services.cloudresourcemanager.model.{
  Binding => ProjectBinding,
  Expr => ProjectExpr,
  GetIamPolicyRequest,
  GetPolicyOptions,
  Policy => ProjectPolicy,
  SetIamPolicyRequest => ProjectSetIamPolicyRequest,
  TestIamPermissionsRequest
}
import com.google.api.services.iam.v1.model.{
  Binding => ServiceAccountBinding,
  CreateServiceAccountKeyRequest,
  CreateServiceAccountRequest,
  Expr => ServiceAccountExpr,
  Policy => ServiceAccountPolicy,
  Role,
  ServiceAccount,
  ServiceAccountKey => GoogleServiceAccountKey,
  SetIamPolicyRequest => ServiceAccountSetIamPolicyRequest
}
import com.google.api.services.iam.v1.{Iam, IamScopes}
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google.GoogleUtilities.RetryPredicates._
import org.broadinstitute.dsde.workbench.google.HttpGoogleIamDAO._
import org.broadinstitute.dsde.workbench.google.IamOperations.{policyVersion, updatePolicy}
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.model.google.iam.IamMemberTypes.IamMemberType
import org.broadinstitute.dsde.workbench.model.google.{iam, _}
import org.broadinstitute.dsde.workbench.model.google.iam.{Binding, Expr, IamMemberTypes, Policy}

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Created by rtitle on 10/2/17.
 */
class HttpGoogleIamDAO(appName: String, googleCredentialMode: GoogleCredentialMode, workbenchMetricBaseName: String)(
  implicit
  system: ActorSystem,
  executionContext: ExecutionContext
) extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName)
    with GoogleIamDAO {

  @deprecated(
    message =
      "This way of instantiating HttpGoogleIamDAO has been deprecated. Please update to use the primary constructor.",
    since = "0.15"
  )
  def this(serviceAccountClientId: String, pemFile: String, appName: String, workbenchMetricBaseName: String)(implicit
    system: ActorSystem,
    executionContext: ExecutionContext
  ) =
    this(appName, Pem(WorkbenchEmail(serviceAccountClientId), new File(pemFile)), workbenchMetricBaseName)

  @deprecated(
    message =
      "This way of instantiating HttpGoogleIamDAO has been deprecated. Please update to use the primary constructor.",
    since = "0.15"
  )
  def this(clientSecrets: GoogleClientSecrets, pemFile: String, appName: String, workbenchMetricBaseName: String)(
    implicit
    system: ActorSystem,
    executionContext: ExecutionContext
  ) =
    this(appName,
         Pem(WorkbenchEmail(clientSecrets.getDetails.get("client_email").toString), new File(pemFile)),
         workbenchMetricBaseName
    )

  override val scopes = List(IamScopes.CLOUD_PLATFORM)

  lazy val cloudResourceManager =
    new CloudResourceManager.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()

  implicit override val service: GoogleInstrumentedService.Value = GoogleInstrumentedService.Iam

  private lazy val iam =
    new Iam.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()

  override def findServiceAccount(serviceAccountProject: GoogleProject,
                                  serviceAccountName: ServiceAccountName
  ): Future[Option[google.ServiceAccount]] =
    findServiceAccount(serviceAccountProject, toServiceAccountEmail(serviceAccountProject, serviceAccountName))

  override def findServiceAccount(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail) = {
    val name = s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}"
    val getter = iam.projects().serviceAccounts().get(name)

    // Return a Future[Option[ServiceAccount]]. The future fails if we get a Google error we don't understand. The Option is None if we get a 404, i.e. the SA doesn't exist.
    val findOption = OptionT(
      retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
        () =>
          Option(executeGoogleRequest(getter))
      } {
        case t: GoogleJsonResponseException if t.getStatusCode == 404 =>
          None
        case t: GoogleJsonResponseException
            if t.getStatusCode == 403 && t.getDetails.getErrors.asScala.head.getMessage
              .equalsIgnoreCase("Unable to extract resource containers.") =>
          // added to catch and fix a google issue that popped up https://console.cloud.google.com/support/cases/detail/17978989?project=broad-dsde-prod
          None
      }
    )

    // Turn it into a Workbench SA type.
    (findOption map { serviceAccount =>
      google.ServiceAccount(ServiceAccountSubjectId(serviceAccount.getUniqueId),
                            WorkbenchEmail(serviceAccount.getEmail),
                            ServiceAccountDisplayName(serviceAccount.getDisplayName)
      )
    }).value
  }

  override def createServiceAccount(serviceAccountProject: GoogleProject,
                                    serviceAccountName: ServiceAccountName,
                                    displayName: ServiceAccountDisplayName
  ): Future[google.ServiceAccount] = {
    val request = new CreateServiceAccountRequest()
      .setAccountId(serviceAccountName.value)
      .setServiceAccount(new ServiceAccount().setDisplayName(displayName.value))
    val inserter = iam.projects().serviceAccounts().create(s"projects/${serviceAccountProject.value}", request)
    retryWithRecover(when5xx,
                     whenUsageLimited,
                     whenGlobalUsageLimited,
                     when404,
                     whenInvalidValueOnBucketCreation,
                     whenNonHttpIOException
    ) { () =>
      executeGoogleRequest(inserter)
    } {
      case t: GoogleJsonResponseException if t.getStatusCode == StatusCodes.NotFound.intValue =>
        throw new WorkbenchException(s"The project [${serviceAccountProject.value}] was not found")
    } map { serviceAccount =>
      google.ServiceAccount(ServiceAccountSubjectId(serviceAccount.getUniqueId),
                            WorkbenchEmail(serviceAccount.getEmail),
                            ServiceAccountDisplayName(serviceAccount.getDisplayName)
      )
    }
  }

  override def removeServiceAccount(serviceAccountProject: GoogleProject,
                                    serviceAccountName: ServiceAccountName
  ): Future[Unit] = {
    val serviceAccountEmail = toServiceAccountEmail(serviceAccountProject, serviceAccountName)
    val name = s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}"
    val deleter = iam.projects().serviceAccounts().delete(name)
    retryWithRecover(when5xx,
                     whenUsageLimited,
                     whenGlobalUsageLimited,
                     when404,
                     whenInvalidValueOnBucketCreation,
                     whenNonHttpIOException
    ) { () =>
      executeGoogleRequest(deleter)
      ()
    } {
      // if the service account is already gone, don't fail
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def testIamPermission(project: GoogleProject,
                                 iamPermissions: Set[IamPermission]
  ): Future[Set[IamPermission]] = {
    val testRequest = new TestIamPermissionsRequest().setPermissions(iamPermissions.map(p => p.value).toList.asJava)
    val request = cloudResourceManager.projects().testIamPermissions(project.value, testRequest)
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(request)
    } map { response =>
      Option(response.getPermissions).getOrElse(Collections.emptyList()).asScala.toSet.map(IamPermission)
    }
  }

  override def addRoles(iamProject: GoogleProject,
                        userEmail: WorkbenchEmail,
                        memberType: IamMemberType,
                        rolesToAdd: Set[String],
                        retryIfGroupDoesNotExist: Boolean = false,
                        condition: Option[Expr] = None
  ): Future[Boolean] =
    modifyIamRoles(iamProject, userEmail, memberType, rolesToAdd, Set.empty, retryIfGroupDoesNotExist, condition)

  override def removeRoles(iamProject: GoogleProject,
                           userEmail: WorkbenchEmail,
                           memberType: IamMemberType,
                           rolesToRemove: Set[String],
                           retryIfGroupDoesNotExist: Boolean = false
  ): Future[Boolean] =
    modifyIamRoles(iamProject, userEmail, memberType, Set.empty, rolesToRemove, retryIfGroupDoesNotExist)

  private def modifyIamRoles(iamProject: GoogleProject,
                             userEmail: WorkbenchEmail,
                             memberType: IamMemberType,
                             rolesToAdd: Set[String],
                             rolesToRemove: Set[String],
                             retryIfGroupDoesNotExist: Boolean,
                             condition: Option[Expr] = None
  ): Future[Boolean] = {
    // Note the project here is the one in which we're removing the IAM roles
    // Retry 409s here as recommended for concurrent modifications of the IAM policy

    val basePredicateList: Seq[Throwable => Boolean] = Seq(when5xx,
                                                           whenUsageLimited,
                                                           whenGlobalUsageLimited,
                                                           when404,
                                                           whenInvalidValueOnBucketCreation,
                                                           whenNonHttpIOException,
                                                           when409
    )
    val finalPredicateList: Seq[Throwable => Boolean] =
      basePredicateList ++ (if (retryIfGroupDoesNotExist) Seq(whenGroupDoesNotExist: Throwable => Boolean)
                            else Nil)

    retry(
      finalPredicateList: _*
    ) { () =>
      updateIamPolicy(iamProject, userEmail, memberType, rolesToAdd, rolesToRemove, condition)
    }
  }

  private def updateIamPolicy(iamProject: GoogleProject,
                              userEmail: WorkbenchEmail,
                              memberType: IamMemberType,
                              rolesToAdd: Set[String],
                              rolesToRemove: Set[String],
                              condition: Option[Expr]
  ): Boolean = {
    // It is important that we call getIamPolicy within the same retry block as we call setIamPolicy
    // getIamPolicy gets the etag that is used in setIamPolicy, the etag is used to detect concurrent
    // modifications and if that happens we need to be sure to get a new etag before retrying setIamPolicy
    val request = new GetIamPolicyRequest().setOptions(new GetPolicyOptions().setRequestedPolicyVersion(policyVersion))
    val existingPolicy = executeGoogleRequest(cloudResourceManager.projects().getIamPolicy(iamProject.value, request))
    val updatedPolicy = updatePolicy(existingPolicy, userEmail, memberType, rolesToAdd, rolesToRemove, condition)

    // Policy objects use Sets so are not sensitive to ordering and duplication
    if (existingPolicy == updatedPolicy) {
      false
    } else {
      val policyRequest = new ProjectSetIamPolicyRequest().setPolicy(updatedPolicy).setUpdateMask("bindings,etag")
      executeGoogleRequest(cloudResourceManager.projects().setIamPolicy(iamProject.value, policyRequest))
      true
    }
  }

  override def getProjectPolicy(iamProject: GoogleProject): Future[ProjectPolicy] =
    retry(when5xx,
          whenUsageLimited,
          whenGlobalUsageLimited,
          when404,
          whenInvalidValueOnBucketCreation,
          whenNonHttpIOException,
          when409
    ) { () =>
      val request =
        new GetIamPolicyRequest().setOptions(new GetPolicyOptions().setRequestedPolicyVersion(policyVersion))
      executeGoogleRequest(cloudResourceManager.projects().getIamPolicy(iamProject.value, request))
    }

  // Note the project here is the one in which we're adding the IAM roles.
  // In this case the serviceAccount acts as a resource, not an identity. Therefore the serviceAccount
  // should live in the provided serviceAccountProject. For more information on service account permissions, see:
  // - https://cloud.google.com/iam/docs/service-accounts#service_account_permissions
  // - https://cloud.google.com/iam/docs/service-accounts#the_service_account_user_role
  // Also a helpful SO answer can be viewed at https://stackoverflow.com/a/61878052/2851999
  override def addIamPolicyBindingOnServiceAccount(serviceAccountProject: GoogleProject,
                                                   serviceAccount: WorkbenchEmail,
                                                   member: WorkbenchEmail,
                                                   rolesToAdd: Set[String]
  ): Future[Unit] =
    getServiceAccountPolicy(serviceAccountProject, serviceAccount).flatMap { policy =>
      val updatedPolicy =
        updatePolicy(policy, member, IamMemberTypes.ServiceAccount, rolesToAdd, Set.empty, None)
      val policyRequest = new ServiceAccountSetIamPolicyRequest().setPolicy(updatedPolicy)
      val request = iam
        .projects()
        .serviceAccounts()
        .setIamPolicy(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccount.value}", policyRequest)
      retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
        executeGoogleRequest(request)
      }.void
    }

  override def addServiceAccountUserRoleForUser(serviceAccountProject: GoogleProject,
                                                serviceAccountEmail: WorkbenchEmail,
                                                userEmail: WorkbenchEmail
  ): Future[Unit] =
    addIamPolicyBindingOnServiceAccount(serviceAccountProject,
                                        serviceAccountEmail,
                                        userEmail,
                                        Set("roles/iam.serviceAccountUser")
    )

  override def createServiceAccountKey(serviceAccountProject: GoogleProject,
                                       serviceAccountEmail: WorkbenchEmail
  ): Future[ServiceAccountKey] = {
    val request = new CreateServiceAccountKeyRequest()
      .setPrivateKeyType("TYPE_GOOGLE_CREDENTIALS_FILE")
      .setKeyAlgorithm("KEY_ALG_RSA_2048")
    val creater = iam
      .projects()
      .serviceAccounts()
      .keys()
      .create(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}", request)
    retry(when5xx,
          whenUsageLimited,
          whenGlobalUsageLimited,
          when404,
          whenInvalidValueOnBucketCreation,
          whenNonHttpIOException
    ) { () =>
      executeGoogleRequest(creater)
    } map googleKeyToWorkbenchKey
  }

  override def removeServiceAccountKey(serviceAccountProject: GoogleProject,
                                       serviceAccountEmail: WorkbenchEmail,
                                       keyId: ServiceAccountKeyId
  ): Future[Unit] = {
    val request = iam
      .projects()
      .serviceAccounts()
      .keys()
      .delete(
        s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}/keys/${keyId.value}"
      )
    retryWithRecover(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) {
      () =>
        executeGoogleRequest(request)
        ()
    } {
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def listServiceAccountKeys(serviceAccountProject: GoogleProject,
                                      serviceAccountEmail: WorkbenchEmail
  ): Future[Seq[ServiceAccountKey]] = {
    val request = iam
      .projects()
      .serviceAccounts()
      .keys()
      .list(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}")

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(request)
    } map { response =>
      Option(response.getKeys).getOrElse(Collections.emptyList()).asScala.toSeq map googleKeyToWorkbenchKey
    }
  }

  override def listUserManagedServiceAccountKeys(
    serviceAccountProject: GoogleProject,
    serviceAccountEmail: WorkbenchEmail
  ): Future[Seq[ServiceAccountKey]] = {
    val request = iam
      .projects()
      .serviceAccounts()
      .keys()
      .list(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}")
      .setKeyTypes(List("USER_MANAGED").asJava)

    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(request)
    } map { response =>
      Option(response.getKeys).getOrElse(Collections.emptyList()).asScala.toSeq map googleKeyToWorkbenchKey
    }
  }

  override def getOrganizationCustomRole(roleName: String): Future[Option[Role]] = {
    val request = iam.organizations().roles().get(roleName)

    retryWithRecover(when5xx, whenUsageLimited, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      Option(executeGoogleRequest(request))
    } {
      case t: GoogleJsonResponseException if t.getStatusCode == 404 =>
        None
    }
  }

  private def googleKeyToWorkbenchKey(googleKey: GoogleServiceAccountKey): ServiceAccountKey =
    ServiceAccountKey(
      ServiceAccountKeyId(googleKey.getName.split('/').last),
      ServiceAccountPrivateKeyData(googleKey.getPrivateKeyData),
      Option(googleKey.getValidAfterTime).flatMap(googleTimestampToInstant),
      Option(googleKey.getValidBeforeTime).flatMap(googleTimestampToInstant)
    )

  private def googleTimestampToInstant(googleTimestamp: String): Option[Instant] =
    Try {
      Instant.from(DateTimeFormatter.ISO_INSTANT.parse(googleTimestamp))
    }.toOption

  private def getServiceAccountPolicy(serviceAccountProject: GoogleProject,
                                      serviceAccountEmail: WorkbenchEmail
  ): Future[Policy] = {
    val request = iam
      .projects()
      .serviceAccounts()
      .getIamPolicy(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}")
      .setOptionsRequestedPolicyVersion(policyVersion)
    retry(when5xx, whenUsageLimited, when404, whenInvalidValueOnBucketCreation, whenNonHttpIOException) { () =>
      executeGoogleRequest(request)
    }
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

  implicit private def fromProjectExpr(projectExpr: ProjectExpr): Expr =
    if (projectExpr == null) {
      null
    } else {
      Expr(projectExpr.getDescription, projectExpr.getExpression, projectExpr.getLocation, projectExpr.getTitle)
    }

  implicit def toProjectExpr(expr: Expr): ProjectExpr =
    if (expr == null) {
      null
    } else {
      new ProjectExpr()
        .setDescription(expr.description)
        .setExpression(expr.expression)
        .setLocation(expr.location)
        .setTitle(expr.title)
    }

  implicit private def fromServiceAccountExpr(serviceAccountExpr: ServiceAccountExpr): Expr =
    if (serviceAccountExpr == null) {
      null
    } else {
      Expr(serviceAccountExpr.getDescription,
           serviceAccountExpr.getExpression,
           serviceAccountExpr.getLocation,
           serviceAccountExpr.getTitle
      )
    }

  implicit def toServiceAccountExpr(expr: Expr): ServiceAccountExpr =
    if (expr == null) {
      null
    } else {
      new ServiceAccountExpr()
        .setDescription(expr.description)
        .setExpression(expr.expression)
        .setLocation(expr.location)
        .setTitle(expr.title)
    }

  implicit private def fromProjectBinding(projectBinding: ProjectBinding): Binding =
    iam.Binding(projectBinding.getRole, projectBinding.getMembers.toSet, projectBinding.getCondition)

  implicit private def fromServiceAccountBinding(serviceAccountBinding: ServiceAccountBinding): Binding =
    iam.Binding(serviceAccountBinding.getRole,
                serviceAccountBinding.getMembers.toSet,
                serviceAccountBinding.getCondition
    )

  implicit def fromProjectPolicy(projectPolicy: ProjectPolicy): Policy =
    Policy(projectPolicy.getBindings.map(fromProjectBinding).toSet, projectPolicy.getEtag)

  implicit def toProjectPolicy(policy: Policy): ProjectPolicy =
    new ProjectPolicy()
      .setBindings(
        policy.bindings
          .map { b =>
            new ProjectBinding().setRole(b.role).setMembers(b.members.toList.asJava).setCondition(b.condition)
          }
          .toList
          .asJava
      )
      .setEtag(policy.etag)
      .setVersion(policyVersion)

  implicit def fromServiceAccountPolicy(serviceAccountPolicy: ServiceAccountPolicy): Policy =
    Policy(serviceAccountPolicy.getBindings.map(fromServiceAccountBinding).toSet, serviceAccountPolicy.getEtag)

  implicit def toServiceAccountPolicy(policy: Policy): ServiceAccountPolicy =
    new ServiceAccountPolicy()
      .setBindings(
        policy.bindings
          .map { b =>
            new ServiceAccountBinding().setRole(b.role).setMembers(b.members.toList.asJava).setCondition(b.condition)
          }
          .toList
          .asJava
      )
      .setEtag(policy.etag)
      .setVersion(policyVersion)

  implicit private def nullSafeList[A](list: java.util.List[A]): List[A] =
    Option(list).map(_.asScala.toList).getOrElse(List.empty[A])
}
