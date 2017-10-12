package org.broadinstitute.dsde.workbench.google

import org.broadinstitute.dsde.workbench.model._

import scala.concurrent.Future

/**
  * Created by rtitle on 10/2/17.
  */
trait GoogleIamDAO {
  /**
    * Creates a service account in the given project.
    * @param googleProject the project in which to create the service account
    * @param serviceAccountId the service account id
    * @param displayName the service account display name
    * @return newly created service account
    */
  def createServiceAccount(googleProject: String, serviceAccountId: WorkbenchUserServiceAccountId, displayName: WorkbenchUserServiceAccountDisplayName): Future[WorkbenchUserServiceAccount]

  /**
    * Adds project-level IAM roles for the given user.
    * @param googleProject the project in which to add the roles
    * @param userEmail the user email address
    * @param rolesToAdd Set of roles to add (example: roles/storage.admin)
    */
  def addIamRolesForUser(googleProject: String, userEmail: WorkbenchUserEmail, rolesToAdd: Set[String]): Future[Unit]

  /**
    * Adds the Service Account Actor role for the given users on the given service account.
    * This allows the users to impersonate as the service account.
    * @param googleProject the project in which to add the roles
    * @param serviceAccountEmail the service account on which to add the Service Account Actor role
    *                               (i.e. the IAM resource).
    * @param userEmail the user email address for which to add Service Account Actor
    */
  def addServiceAccountActorRoleForUser(googleProject: String, serviceAccountEmail: WorkbenchUserServiceAccountEmail, userEmail: WorkbenchUserEmail): Future[Unit]
}