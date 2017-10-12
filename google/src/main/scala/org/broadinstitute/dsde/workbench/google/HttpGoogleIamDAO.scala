package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import cats.instances.future._
import cats.instances.list._
import cats.instances.map._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.semigroup._
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.{GoogleClientSecrets, GoogleCredential}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.cloudresourcemanager.CloudResourceManager
import com.google.api.services.cloudresourcemanager.model.{Binding => ProjectBinding, Policy => ProjectPolicy, SetIamPolicyRequest => ProjectSetIamPolicyRequest}
import com.google.api.services.iam.v1.model.{CreateServiceAccountRequest, ServiceAccount, Binding => ServiceAccountBinding, Policy => ServiceAccountPolicy, SetIamPolicyRequest => ServiceAccountSetIamPolicyRequest}
import com.google.api.services.iam.v1.{Iam, IamScopes}
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.google.HttpGoogleIamDAO._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by rtitle on 10/2/17.
  */
class HttpGoogleIamDAO(clientSecrets: GoogleClientSecrets,
                       pemFile: String,
                       appName: String,
                       override val workbenchMetricBaseName: String)
                      (implicit val system: ActorSystem, val executionContext: ExecutionContext) extends GoogleIamDAO with GoogleUtilities {

  lazy val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  lazy val jsonFactory = JacksonFactory.getDefaultInstance
  lazy val scopes = List(IamScopes.CLOUD_PLATFORM)

  lazy val serviceAccountClientId: String = clientSecrets.getDetails.get("client_email").toString

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

  override def createServiceAccount(googleProject: String, serviceAccountId: WorkbenchUserServiceAccountId, displayName: WorkbenchUserServiceAccountDisplayName): Future[WorkbenchUserServiceAccount] = {
    val request = new CreateServiceAccountRequest().setAccountId(serviceAccountId.value)
      .setServiceAccount(new ServiceAccount().setDisplayName(displayName.value))
    val inserter = iam.projects().serviceAccounts().create(s"projects/$googleProject", request)
    retryWhen500orGoogleError { () =>
      executeGoogleRequest(inserter)
    } map { serviceAccount =>
      WorkbenchUserServiceAccount(serviceAccountId, WorkbenchUserServiceAccountEmail(serviceAccount.getEmail), displayName)
    }
  }

  override def removeServiceAccount(googleProject: String, serviceAccountId: WorkbenchUserServiceAccountId): Future[Unit] = {
    val name = s"projects/$googleProject/serviceAccounts/${serviceAccountId.value}"
    val deleter = iam.projects().serviceAccounts().delete(name)
    retryWhen500orGoogleError { () =>
      executeGoogleRequest(deleter)
    }.void
  }

  override def addIamRolesForUser(googleProject: String, userEmail: WorkbenchUserEmail, rolesToAdd: Set[String]): Future[Unit] = {
    getProjectPolicy(googleProject).flatMap { policy =>
      val updatedPolicy = updatePolicy(policy, userEmail, rolesToAdd)
      val policyRequest = new ProjectSetIamPolicyRequest().setPolicy(updatedPolicy)
      val request = cloudResourceManager.projects().setIamPolicy(s"projects/$googleProject", policyRequest)
      retryWhen500orGoogleError { () =>
        executeGoogleRequest(request)
      }.void
    }
  }

  override def addServiceAccountActorRoleForUser(googleProject: String, serviceAccountEmail: WorkbenchUserServiceAccountEmail, userEmail: WorkbenchUserEmail): Future[Unit] = {
    getServiceAccountPolicy(googleProject, serviceAccountEmail).flatMap { policy =>
      val updatedPolicy = updatePolicy(policy, userEmail, Set("roles/iam.serviceAccountActor"))
      val policyRequest = new ServiceAccountSetIamPolicyRequest().setPolicy(updatedPolicy)
      val request = iam.projects().serviceAccounts().setIamPolicy(s"projects/$googleProject/serviceAccounts/${serviceAccountEmail.value}", policyRequest)
      retryWhen500orGoogleError { () =>
        executeGoogleRequest(request)
      }.void
    }
  }

  private def getProjectPolicy(googleProject: String): Future[Policy] = {
    val request = cloudResourceManager.projects().getIamPolicy(s"projects/$googleProject", null)
    retryWhen500orGoogleError { () =>
      executeGoogleRequest(request)
    }
  }

  private def getServiceAccountPolicy(googleProject: String, serviceAccountEmail: WorkbenchUserServiceAccountEmail): Future[Policy] = {
    val request = iam.projects().serviceAccounts().getIamPolicy(s"projects/$googleProject/serviceAccounts/${serviceAccountEmail.value}")
    retryWhen500orGoogleError { () =>
      executeGoogleRequest(request)
    }
  }

  /**
    * Read-modify-write a Policy to insert new bindings for the given member and roles.
    */
  private def updatePolicy(policy: Policy, userEmail: WorkbenchUserEmail, rolesToAdd: Set[String]): Policy = {
    // current bindings grouped by role
    val curMembersByRole: Map[String, List[String]] = policy.bindings.foldMap { binding =>
      Map(binding.role -> binding.members)
    }

    // new bindings grouped by role
    val memberType = if (userEmail.isServiceAccount) "serviceAccount" else "user"
    val newMembersByRole: Map[String, List[String]] = rolesToAdd.toList.foldMap { role =>
      Map(role -> List(s"$memberType:${userEmail.value}"))
    }

    // current bindings merged with new bindings
    val bindings = (curMembersByRole |+| newMembersByRole).map { case (role, members) =>
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
