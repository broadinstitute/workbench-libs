package org.broadinstitute.dsde.workbench.google

import ca.mrvisser.sealerate
import com.google.api.services.cloudresourcemanager.model.{Policy => ProjectPolicy}
import com.google.api.services.iam.v1.model.Role
import org.broadinstitute.dsde.workbench.google.GoogleIamDAO.MemberType
import org.broadinstitute.dsde.workbench.google.IamModel.Expr
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.model.google._

import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by rtitle on 10/2/17.
 */
trait GoogleIamDAO {
  /*
   * Constructs a service account email from a project and account id.
   * Relies on spooky knowledge of how Google constructs SA emails, which isn't the best.
   */
  protected def toServiceAccountEmail(serviceAccountProject: GoogleProject,
                                      serviceAccountName: ServiceAccountName
  ): WorkbenchEmail =
    WorkbenchEmail(s"$serviceAccountName@$serviceAccountProject.iam.gserviceaccount.com")

  /**
   * Looks for a service account in the given project.
   *
   * @param serviceAccountProject the project in which to create the service account
   * @param serviceAccountName    the service account name
   * @return An option representing either finding the SA, or not, wrapped in a Future, representing any other failures.
   */
  def findServiceAccount(serviceAccountProject: GoogleProject,
                         serviceAccountName: ServiceAccountName
  ): Future[Option[ServiceAccount]]

  /**
   * Looks for a service account in the given project.
   *
   * @param serviceAccountProject the project in which to create the service account
   * @param serviceAccountEmail   the service account email
   * @return An option representing either finding the SA, or not, wrapped in a Future, representing any other failures.
   */
  def findServiceAccount(serviceAccountProject: GoogleProject,
                         serviceAccountEmail: WorkbenchEmail
  ): Future[Option[ServiceAccount]]

  /**
   * Creates a service account in the given project.
   *
   * @param serviceAccountProject the project in which to create the service account
   * @param serviceAccountName    the service account name, which Google will use to construct the SA's email
   * @param displayName           the service account display name
   * @return newly created service account
   */
  def createServiceAccount(serviceAccountProject: GoogleProject,
                           serviceAccountName: ServiceAccountName,
                           displayName: ServiceAccountDisplayName
  ): Future[ServiceAccount]

  /**
   * Get or create a service account in the given project.
   *
   * @param serviceAccountProject the project in which to create the service account
   * @param serviceAccountName    the service account name
   * @param displayName           the service account display name
   * @return the service account. Note that it may not have the same display name as the request you made if one already existed.
   */
  def getOrCreateServiceAccount(
    serviceAccountProject: GoogleProject,
    serviceAccountName: ServiceAccountName,
    displayName: ServiceAccountDisplayName
  )(implicit executionContext: ExecutionContext): Future[ServiceAccount] =
    findServiceAccount(serviceAccountProject, serviceAccountName) flatMap {
      case None                 => createServiceAccount(serviceAccountProject, serviceAccountName, displayName)
      case Some(serviceAccount) => Future.successful(serviceAccount)
    }

  /**
   * Removes a service account in the given project.
   *
   * @param serviceAccountProject the project in which to remove the service account
   * @param serviceAccountName    the service account name
   */
  def removeServiceAccount(serviceAccountProject: GoogleProject, serviceAccountName: ServiceAccountName): Future[Unit]

  /**
   * Test that the caller has a specified permission on the project.
   *
   * @param project        the project in which to test permissions.
   * @param iamPermissions a set of IAM permissions (not IAM roles) to test.
   * @return the set of iam permissions allowed to the caller overlapping with the supplied permission set.
   */
  def testIamPermission(project: GoogleProject, iamPermissions: Set[IamPermission]): Future[Set[IamPermission]]

  /**
   * Adds project-level IAM roles for the given user.
   * This method will perform a read-modify-write of the project's IAM policy, and return a Boolean
   * indicating whether a change was actually made.
   *
   * @param iamProject the project in which to add the roles
   * @param email      the user email address
   * @param rolesToAdd Set of roles to add (example: roles/storage.admin)
   * @return true if the policy was updated; false otherwise.
   */
  @deprecated(message = "Please use the generic method with {{{ memberType = MemberType.User }}}.", since = "0.21")
  def addIamRolesForUser(iamProject: GoogleProject, email: WorkbenchEmail, rolesToAdd: Set[String]): Future[Boolean] =
    addIamRoles(iamProject: GoogleProject, email: WorkbenchEmail, MemberType.User, rolesToAdd: Set[String])

  /**
   * Removes project-level IAM roles for the given user.
   * This method will perform a read-modify-write of the project's IAM policy, and return a Boolean
   * indicating whether a change was actually made.
   *
   * @param iamProject    the google project in which to remove the roles
   * @param email         the user email address
   * @param rolesToRemove Set of roles to remove (example: roles/dataproc.worker)
   * @return true if the policy was updated; false otherwise.
   */
  @deprecated(message = "Please use the generic method with {{{ memberType = MemberType.User }}}.", since = "0.21")
  def removeIamRolesForUser(iamProject: GoogleProject,
                            email: WorkbenchEmail,
                            rolesToRemove: Set[String]
  ): Future[Boolean] =
    removeIamRoles(iamProject: GoogleProject, email: WorkbenchEmail, MemberType.User, rolesToRemove: Set[String])

  /**
   * Adds project-level IAM roles for the given member type.
   * This method will perform a read-modify-write of the project's IAM policy, and return a Boolean
   * indicating whether a change was actually made.
   *
   * @param iamProject               the project in which to add the roles
   * @param email                    the email address
   * @param memberType               the type of member (e.g. 'user', 'group', 'service account')
   * @param rolesToAdd               Set of roles to add (example: roles/storage.admin)
   * @param retryIfGroupDoesNotExist optional parameter to rerun if the group does not exist (yet), since Google can
   *                                 take up to 1 hour to propagate some changes.
   * @return true if the policy was updated; false otherwise.
   */
  def addIamRoles(iamProject: GoogleProject,
                  email: WorkbenchEmail,
                  memberType: MemberType,
                  rolesToAdd: Set[String],
                  retryIfGroupDoesNotExist: Boolean = false,
                  condition: Option[Expr] = None
  ): Future[Boolean]

  /**
   * Removes project-level IAM roles for the given member type.
   * This method will perform a read-modify-write of the project's IAM policy, and return a Boolean
   * indicating whether a change was actually made.
   *
   * @param iamProject               the google project in which to remove the roles
   * @param email                    the email address
   * @param memberType               the type of member (e.g. 'user', 'group', 'service account')
   * @param rolesToRemove            Set of roles to remove (example: roles/dataproc.worker)
   * @param retryIfGroupDoesNotExist optional parameter to rerun if the group does not exist (yet), since Google can
   *                                 take up to 1 hour to propagate some changes.
   * @return true if the policy was updated; false otherwise.
   */
  def removeIamRoles(iamProject: GoogleProject,
                     email: WorkbenchEmail,
                     memberType: MemberType,
                     rolesToRemove: Set[String],
                     retryIfGroupDoesNotExist: Boolean = false
  ): Future[Boolean]

  /**
   * Gets ProjectPolicy which includes project-level IAM roles for the given project
   *
   * @param iamProject the google project to get the policy for
   * @return the policy of the project, which lists all roles
   */
  def getProjectPolicy(iamProject: GoogleProject): Future[ProjectPolicy]

  /**
   * @param serviceAccountProject the google project where serviceAccount lives
   * @param serviceAccount        the service account (i.e. the IAM resource) to which to add the policy binding
   * @param member                the user email address for which to add the roles to
   * @param rolesToAdd            set of roles to add to the member
   * @return
   */
  def addIamPolicyBindingOnServiceAccount(serviceAccountProject: GoogleProject,
                                          serviceAccount: WorkbenchEmail,
                                          member: WorkbenchEmail,
                                          rolesToAdd: Set[String]
  ): Future[Unit]

  /**
   * Adds the Service Account User role for the given users on the given service account.
   * This allows the users to impersonate as the service account.
   *
   * @param serviceAccountProject the project in which to add the roles
   * @param serviceAccountEmail   the service account on which to add the Service Account User role
   *                              (i.e. the IAM resource).
   * @param email                 the user email address for which to add Service Account User
   */
  def addServiceAccountUserRoleForUser(serviceAccountProject: GoogleProject,
                                       serviceAccountEmail: WorkbenchEmail,
                                       email: WorkbenchEmail
  ): Future[Unit]

  /**
   * Creates a user-managed key for the given service account.
   *
   * @param serviceAccountProject the google project the service account resides in
   * @param serviceAccountEmail   the service account email
   * @return instance of ServiceAccountKey
   */
  def createServiceAccountKey(serviceAccountProject: GoogleProject,
                              serviceAccountEmail: WorkbenchEmail
  ): Future[ServiceAccountKey]

  /**
   * Deletes a user-managed key for the given service account.
   *
   * @param serviceAccountProject the google project the service account resides in
   * @param serviceAccountEmail   the service account email
   * @param keyId                 the key identifier
   */
  def removeServiceAccountKey(serviceAccountProject: GoogleProject,
                              serviceAccountEmail: WorkbenchEmail,
                              keyId: ServiceAccountKeyId
  ): Future[Unit]

  /**
   * Lists keys associated with a given service account.
   *
   * @param serviceAccountProject the google project the service account resides in
   * @param serviceAccountEmail   the service account email
   * @return list of service account keys
   */
  def listServiceAccountKeys(serviceAccountProject: GoogleProject,
                             serviceAccountEmail: WorkbenchEmail
  ): Future[Seq[ServiceAccountKey]]

  /**
   * Lists user managed keys associated with a given service account.
   *
   * @param serviceAccountProject the google project the service account resides in
   * @param serviceAccountEmail   the service account email
   * @return list of service account keys
   */
  def listUserManagedServiceAccountKeys(serviceAccountProject: GoogleProject,
                                        serviceAccountEmail: WorkbenchEmail
  ): Future[Seq[ServiceAccountKey]]

  /**
   * see https://cloud.google.com/iam/docs/reference/rest/v1/organizations.roles/get
   * @param roleName passed as-is to name parameter of google api
   * @return
   */
  def getOrganizationCustomRole(roleName: String): Future[Option[Role]]
}

object GoogleIamDAO {

  /**
   * Typing for the Google IAM member types as described at https://cloud.google.com/iam/docs/overview
   */
  sealed trait MemberType extends Serializable with Product
  object MemberType {
    final case object User extends MemberType {
      override def toString = "user"
    }
    final case object Group extends MemberType {
      override def toString = "group"
    }
    final case object ServiceAccount extends MemberType {
      override def toString = "serviceAccount"
    }
    final case object Domain extends MemberType {
      override def toString = "domain"
    }

    val stringToMemberType: Map[String, MemberType] = sealerate.collect[MemberType].map(p => (p.toString, p)).toMap
  }
}
