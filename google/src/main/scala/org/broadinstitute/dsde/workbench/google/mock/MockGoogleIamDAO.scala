package org.broadinstitute.dsde.workbench.google.mock

import org.broadinstitute.dsde.workbench.google.GoogleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.broadinstitute.dsde.workbench.model._

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by rtitle on 10/2/17.
  */
class MockGoogleIamDAO(implicit executionContext: ExecutionContext) extends GoogleIamDAO {

  val serviceAccounts: mutable.Map[WorkbenchEmail, WorkbenchUserServiceAccount] = new TrieMap()

  override def findServiceAccount(serviceAccountProject: GoogleProject, serviceAccountId: WorkbenchUserServiceAccountId): Future[Option[WorkbenchUserServiceAccount]] = {
    val email = WorkbenchUserServiceAccountEmail(s"$serviceAccountId@test-project.iam.gserviceaccount.com")
    if( serviceAccounts.contains(email) ) {
      Future.successful(Some(serviceAccounts(email)))
    } else {
      Future.successful(None)
    }
  }

  override def createServiceAccount(googleProject: GoogleProject, serviceAccountId: WorkbenchUserServiceAccountId, displayName: WorkbenchUserServiceAccountDisplayName): Future[WorkbenchUserServiceAccount] = {
    val email = WorkbenchUserServiceAccountEmail(s"$serviceAccountId@test-project.iam.gserviceaccount.com")
    val sa = WorkbenchUserServiceAccount(serviceAccountId, email, displayName)
    serviceAccounts += email -> sa
    Future.successful(sa)
  }

  override def removeServiceAccount(googleProject: GoogleProject, serviceAccountId: WorkbenchUserServiceAccountId): Future[Unit] = {
    serviceAccounts -= WorkbenchUserServiceAccountEmail(s"${serviceAccountId.value}@test-project.iam.gserviceaccount.com")
    Future.successful(())
  }

  override def addIamRolesForUser(googleProject: GoogleProject, userEmail: WorkbenchEmail, rolesToAdd: Set[String]): Future[Unit] = {
    Future.successful(())
  }

  override def removeIamRolesForUser(googleProject: GoogleProject, userEmail: WorkbenchEmail, rolesToRemove: Set[String]): Future[Unit] = {
    Future.successful(())
  }

  override def addServiceAccountUserRoleForUser(googleProject: GoogleProject, serviceAccountEmail: WorkbenchUserServiceAccountEmail, userEmail: WorkbenchEmail): Future[Unit] = {
    if (serviceAccounts.contains(serviceAccountEmail)) {
      Future.successful(())
    } else {
      Future.failed(new Exception(s"Unknown service account $userEmail"))
    }
  }
}
