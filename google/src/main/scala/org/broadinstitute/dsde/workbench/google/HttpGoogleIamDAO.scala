package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import cats.instances.future._
import cats.instances.list._
import cats.instances.map._
import cats.data.OptionT
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.semigroup._
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.{GoogleClientSecrets, GoogleCredential}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.cloudresourcemanager.CloudResourceManager
import com.google.api.services.cloudresourcemanager.model.{Binding => ProjectBinding, Policy => ProjectPolicy, SetIamPolicyRequest => ProjectSetIamPolicyRequest}
import com.google.api.services.iam.v1.model.{CreateServiceAccountRequest, ServiceAccount, Binding => ServiceAccountBinding, Policy => ServiceAccountPolicy, SetIamPolicyRequest => ServiceAccountSetIamPolicyRequest}
import com.google.api.services.iam.v1.{Iam, IamScopes}
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.google.HttpGoogleIamDAO._
import org.broadinstitute.dsde.workbench.google.model._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by rtitle on 10/2/17.
  */
class HttpGoogleIamDAO(serviceAccountClientId: String,
                       pemFile: String,
                       appName: String,
                       override val workbenchMetricBaseName: String)
                      (implicit val system: ActorSystem, val executionContext: ExecutionContext) extends GoogleIamDAO with GoogleUtilities {

  def this(clientSecrets: GoogleClientSecrets,
           pemFile: String,
           appName: String,
           workbenchMetricBaseName: String)
          (implicit system: ActorSystem, executionContext: ExecutionContext) = {
    this(clientSecrets.getDetails.get("client_email").toString, pemFile, appName, workbenchMetricBaseName)
  }

  lazy val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  lazy val jsonFactory = JacksonFactory.getDefaultInstance
  lazy val scopes = List(IamScopes.CLOUD_PLATFORM)

  lazy val credential: Credential = {
    new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(serviceAccountClientId)
      .setServiceAccountScopes(scopes.asJava)
      .setServiceAccountPrivateKeyFromPemFile(new java.io.File(pemFile))
      .build()
  }

  lazy val iam = new Iam.Builder(httpTransport, jsonFactory, credential).setApplicationName(appName).build()
  lazy val cloudResourceManager = new CloudResourceManager.Builder(httpTransport, jsonFactory, credential).setApplicationName(appName).build()

  implicit val service = GoogleInstrumentedService.Iam

  override def findServiceAccount(serviceAccountProject: GoogleProject, serviceAccountId: WorkbenchUserServiceAccountId): Future[Option[WorkbenchUserServiceAccount]] = {
    val serviceAccountEmail = toServiceAccountEmail(serviceAccountProject, serviceAccountId)
    val name = s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}"
    val getter = iam.projects().serviceAccounts().get(name)

    //Return a Future[Option[ServiceAccount]]. The future fails if we get a Google error we don't understand. The Option is None if we get a 404, i.e. the SA doesn't exist.
    val findOption = OptionT(retryWithRecoverWhen500orGoogleError { () =>
        Option(executeGoogleRequest(getter))
    } {
      case t: GoogleJsonResponseException if t.getStatusCode == 404 =>
        None
    })

    //Turn it into a Workbench SA type.
    (findOption map { serviceAccount =>
        WorkbenchUserServiceAccount(
          WorkbenchUserServiceAccountUniqueId(serviceAccount.getUniqueId),
          WorkbenchUserServiceAccountEmail(serviceAccount.getEmail),
          WorkbenchUserServiceAccountDisplayName(serviceAccount.getDisplayName))
    }).value
  }

  override def createServiceAccount(serviceAccountProject: GoogleProject, serviceAccountId: WorkbenchUserServiceAccountId, displayName: WorkbenchUserServiceAccountDisplayName): Future[WorkbenchUserServiceAccount] = {
    val request = new CreateServiceAccountRequest().setAccountId(serviceAccountId.value)
      .setServiceAccount(new ServiceAccount().setDisplayName(displayName.value))
    val inserter = iam.projects().serviceAccounts().create(s"projects/${serviceAccountProject.value}", request)
    retryWhen500orGoogleError { () =>
      executeGoogleRequest(inserter)
    } map { serviceAccount =>
      WorkbenchUserServiceAccount(WorkbenchUserServiceAccountUniqueId(serviceAccount.getUniqueId), WorkbenchUserServiceAccountEmail(serviceAccount.getEmail), WorkbenchUserServiceAccountDisplayName(serviceAccount.getDisplayName))
    }
  }

  override def removeServiceAccount(serviceAccountProject: GoogleProject, serviceAccountId: WorkbenchUserServiceAccountId): Future[Unit] = {
    val serviceAccountEmail = toServiceAccountEmail(serviceAccountProject, serviceAccountId)
    val name = s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}"
    val deleter = iam.projects().serviceAccounts().delete(name)
    retryWithRecoverWhen500orGoogleError { () =>
      executeGoogleRequest(deleter)
      ()
    } {
      // if the service account is already gone, don't fail
      case e: HttpResponseException if e.getStatusCode == StatusCodes.NotFound.intValue => ()
    }
  }

  override def addIamRolesForUser(iamProject: GoogleProject, userEmail: WorkbenchEmail, rolesToAdd: Set[String]): Future[Unit] = {
    // Note the project here is the one in which we're adding the IAM roles
    getProjectPolicy(iamProject).flatMap { policy =>
      val updatedPolicy = updatePolicy(policy, userEmail, rolesToAdd, Set.empty)
      val policyRequest = new ProjectSetIamPolicyRequest().setPolicy(updatedPolicy)
      val request = cloudResourceManager.projects().setIamPolicy(iamProject.value, policyRequest)
      retryWhen500orGoogleError { () =>
        executeGoogleRequest(request)
      }.void
    }
  }

  override def removeIamRolesForUser(iamProject: GoogleProject, userEmail: WorkbenchEmail, rolesToRemove: Set[String]): Future[Unit] = {
    // Note the project here is the one in which we're removing the IAM roles
    getProjectPolicy(iamProject).flatMap { policy =>
      val updatedPolicy = updatePolicy(policy, userEmail, Set.empty, rolesToRemove)
      val policyRequest = new ProjectSetIamPolicyRequest().setPolicy(updatedPolicy)
      val request = cloudResourceManager.projects().setIamPolicy(iamProject.value, policyRequest)
      retryWhen500orGoogleError { () =>
        executeGoogleRequest(request)
      }.void
    }
  }

  override def addServiceAccountUserRoleForUser(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchUserServiceAccountEmail, userEmail: WorkbenchEmail): Future[Unit] = {
    // Note the project here is the one in which we're adding the IAM roles.
    // In this case the serviceAccountEmail acts as a resource, not an identity. Therefore the serviceAccountEmail
    // should live in the provided serviceAccountProject. For more information on service account permissions, see:
    // - https://cloud.google.com/iam/docs/service-accounts#service_account_permissions
    // - https://cloud.google.com/iam/docs/service-accounts#the_service_account_user_role
    getServiceAccountPolicy(serviceAccountProject, serviceAccountEmail).flatMap { policy =>
      val updatedPolicy = updatePolicy(policy, userEmail, Set("roles/iam.serviceAccountUser"), Set.empty)
      val policyRequest = new ServiceAccountSetIamPolicyRequest().setPolicy(updatedPolicy)
      val request = iam.projects().serviceAccounts().setIamPolicy(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}", policyRequest)
      retryWhen500orGoogleError { () =>
        executeGoogleRequest(request)
      }.void
    }
  }

  private def getProjectPolicy(googleProject: GoogleProject): Future[Policy] = {
    val request = cloudResourceManager.projects().getIamPolicy(googleProject.value, null)
    retryWhen500orGoogleError { () =>
      executeGoogleRequest(request)
    }
  }

  private def getServiceAccountPolicy(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchUserServiceAccountEmail): Future[Policy] = {
    val request = iam.projects().serviceAccounts().getIamPolicy(s"projects/${serviceAccountProject.value}/serviceAccounts/${serviceAccountEmail.value}")
    retryWhen500orGoogleError { () =>
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

    Policy(bindings)
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
  private case class Policy(bindings: List[Binding])

  private implicit def fromProjectBinding(projectBinding: ProjectBinding): Binding = {
    Binding(projectBinding.getRole, projectBinding.getMembers)
  }

  private implicit def fromServiceAccountBinding(serviceAccountBinding: ServiceAccountBinding): Binding = {
    Binding(serviceAccountBinding.getRole, serviceAccountBinding.getMembers)
  }

  private implicit def fromProjectPolicy(projectPolicy: ProjectPolicy): Policy = {
    Policy(projectPolicy.getBindings.map(fromProjectBinding))
  }

  private implicit def fromServiceAccountPolicy(serviceAccountPolicy: ServiceAccountPolicy): Policy = {
    Policy(serviceAccountPolicy.getBindings.map(fromServiceAccountBinding))
  }

  private implicit def toServiceAccountPolicy(policy: Policy): ServiceAccountPolicy = {
    new ServiceAccountPolicy().setBindings(policy.bindings.map { b =>
      new ServiceAccountBinding().setRole(b.role).setMembers(b.members.asJava)
    }.asJava)
  }

  private implicit def toProjectPolicy(policy: Policy): ProjectPolicy = {
    new ProjectPolicy().setBindings(policy.bindings.map { b =>
      new ProjectBinding().setRole(b.role).setMembers(b.members.asJava)
    }.asJava)
  }

  private implicit def nullSafeList[A](list: java.util.List[A]): List[A] = {
    Option(list).map(_.asScala.toList).getOrElse(List.empty[A])
  }
}
