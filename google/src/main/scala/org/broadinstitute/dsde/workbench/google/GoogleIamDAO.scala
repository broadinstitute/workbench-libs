package org.broadinstitute.dsde.workbench.google

import org.broadinstitute.dsde.workbench.google.model.GoogleProject
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
  def createServiceAccount(googleProject: GoogleProject, serviceAccountId: WorkbenchUserServiceAccountId, displayName: WorkbenchUserServiceAccountDisplayName): Future[WorkbenchUserServiceAccount]

  /**
    * Removes a service account in the given project.
    * @param googleProject the project in which to remove the service account
    * @param serviceAccountId the service account id
    */
  def removeServiceAccount(googleProject: GoogleProject, serviceAccountId: WorkbenchUserServiceAccountId): Future[Unit]

  /**
    * Adds project-level IAM roles for the given user.
    * @param googleProject the project in which to add the roles
    * @param email the user email address
    * @param rolesToAdd Set of roles to add (example: roles/storage.admin)
    */
  def addIamRolesForUser(googleProject: GoogleProject, email: WorkbenchEmail, rolesToAdd: Set[String]): Future[Unit]

  /**
    * Removes project-level IAM roles for the given user.
    * @param googleProject the google project in which to remove the roles
    * @param email the user email address
    * @param rolesToRemove Set of roles to remove (example: roles/dataproc.worker)
    */
  def removeIamRolesForUser(googleProject: GoogleProject, email: WorkbenchEmail, rolesToRemove: Set[String]): Future[Unit]

  /**
    * Adds the Service Account Actor role for the given users on the given service account.
    * This allows the users to impersonate as the service account.
    * @param googleProject the project in which to add the roles
    * @param serviceAccountEmail the service account on which to add the Service Account Actor role
    *                               (i.e. the IAM resource).
    * @param email the user email address for which to add Service Account Actor
    */
  def addServiceAccountActorRoleForUser(googleProject: GoogleProject, serviceAccountEmail: WorkbenchUserServiceAccountEmail, email: WorkbenchEmail): Future[Unit]
}