package org.broadinstitute.dsde.workbench.google.mock

import org.broadinstitute.dsde.workbench.google.GoogleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.broadinstitute.dsde.workbench.model._

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
  * Created by rtitle on 10/2/17.
  */
class MockGoogleIamDAO(implicit executionContext: ExecutionContext) extends GoogleIamDAO {

  val serviceAccounts: mutable.Map[WorkbenchEmail, WorkbenchUserServiceAccount] = new TrieMap()
  val serviceAccountKeys: mutable.Map[WorkbenchUserServiceAccountEmail, WorkbenchUserServiceAccountKey] = new TrieMap()

  override def findServiceAccount(serviceAccountProject: GoogleProject, serviceAccountName: WorkbenchUserServiceAccountName): Future[Option[WorkbenchUserServiceAccount]] = {
    val email = WorkbenchUserServiceAccountEmail(s"$serviceAccountName@$serviceAccountName.iam.gserviceaccount.com")
    if( serviceAccounts.contains(email) ) {
      Future.successful(Some(serviceAccounts(email)))
    } else {
      Future.successful(None)
    }
  }

  override def createServiceAccount(googleProject: GoogleProject, serviceAccountName: WorkbenchUserServiceAccountName, displayName: WorkbenchUserServiceAccountDisplayName): Future[WorkbenchUserServiceAccount] = {
    val email = toServiceAccountEmail(googleProject, serviceAccountName)
    val uniqueId = WorkbenchUserServiceAccountSubjectId(Random.nextLong.toString)
    val sa = WorkbenchUserServiceAccount(uniqueId, email, displayName)
    serviceAccounts += email -> sa
    Future.successful(sa)
  }

  override def removeServiceAccount(googleProject: GoogleProject, serviceAccountName: WorkbenchUserServiceAccountName): Future[Unit] = {
    serviceAccounts -= toServiceAccountEmail(googleProject, serviceAccountName)
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

  override def createServiceAccountKey(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchUserServiceAccountEmail): Future[WorkbenchUserServiceAccountKey] = {
    val key = WorkbenchUserServiceAccountKey(WorkbenchUserServiceAccountKeyId("123"), WorkbenchUserServiceAccountPrivateKeyData("abcdefg"))
    serviceAccountKeys += serviceAccountEmail -> key
    Future.successful(key)
  }

  override def removeServiceAccountKey(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchUserServiceAccountEmail, keyId: WorkbenchUserServiceAccountKeyId): Future[Unit] = {
    serviceAccounts -= serviceAccountEmail
    Future.successful(())
  }
}
