package org.broadinstitute.dsde.workbench.google.mock

import org.broadinstitute.dsde.workbench.google.GoogleIamDAO
import org.broadinstitute.dsde.workbench.model._

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by rtitle on 10/2/17.
  */
class MockGoogleIamDAO(implicit executionContext: ExecutionContext) extends GoogleIamDAO {

  val serviceAccounts: mutable.Map[WorkbenchEmail, WorkbenchUserServiceAccount] = new TrieMap()

  override def createServiceAccount(googleProject: String, serviceAccountId: WorkbenchUserServiceAccountId, displayName: WorkbenchUserServiceAccountDisplayName): Future[WorkbenchUserServiceAccount] = {
    val email = WorkbenchUserServiceAccountEmail(s"$serviceAccountId@test-project.iam.gserviceaccount.com")
    val sa = WorkbenchUserServiceAccount(serviceAccountId, email, displayName)
    serviceAccounts += email -> sa
    Future.successful(sa)
  }

  override def removeServiceAccount(googleProject: String, serviceAccountId: WorkbenchUserServiceAccountId): Future[Unit] = {
    serviceAccounts -= WorkbenchUserServiceAccountEmail(s"${serviceAccountId.value}@test-project.iam.gserviceaccount.com")
    Future.successful(())
  }

  override def addIamRolesForUser(googleProject: String, userEmail: WorkbenchUserEmail, rolesToAdd: Set[String]): Future[Unit] = {
    Future.successful(())
  }

  override def addServiceAccountActorRoleForUser(googleProject: String, serviceAccountEmail: WorkbenchUserServiceAccountEmail, userEmail: WorkbenchUserEmail): Future[Unit] = {
    if (serviceAccounts.contains(serviceAccountEmail)) {
      Future.successful(())
    } else {
      Future.failed(new Exception(s"Unknown service account $userEmail"))
    }
  }
}
