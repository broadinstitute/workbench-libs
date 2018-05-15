package org.broadinstitute.dsde.workbench.google.mock

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.{Base64, UUID}

import org.broadinstitute.dsde.workbench.google.GoogleIamDAO
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.model.google._

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
  * Created by rtitle on 10/2/17.
  */
class MockGoogleIamDAO(implicit executionContext: ExecutionContext) extends GoogleIamDAO {

  val serviceAccounts: mutable.Map[WorkbenchEmail, ServiceAccount] = new TrieMap()
  val serviceAccountKeys: mutable.Map[WorkbenchEmail, mutable.Map[ServiceAccountKeyId, ServiceAccountKey]] = new TrieMap()

  override def findServiceAccount(serviceAccountProject: GoogleProject, serviceAccountName: ServiceAccountName): Future[Option[ServiceAccount]] = {
    val email = toServiceAccountEmail(serviceAccountProject, serviceAccountName)
    findServiceAccount(serviceAccountProject, email)
  }

  override def findServiceAccount(serviceAccountProject: GoogleProject, email: WorkbenchEmail) = {
    if (serviceAccounts.contains(email)) {
      Future.successful(Some(serviceAccounts(email)))
    } else {
      Future.successful(None)
    }
  }

  override def createServiceAccount(googleProject: GoogleProject, serviceAccountName: ServiceAccountName, displayName: ServiceAccountDisplayName): Future[ServiceAccount] = {
    val email = toServiceAccountEmail(googleProject, serviceAccountName)
    val uniqueId = ServiceAccountSubjectId(Random.nextLong.toString)
    val sa = ServiceAccount(uniqueId, email, displayName)
    serviceAccounts += email -> sa
    serviceAccountKeys += email -> new TrieMap()
    Future.successful(sa)
  }

  override def removeServiceAccount(googleProject: GoogleProject, serviceAccountName: ServiceAccountName): Future[Unit] = {
    serviceAccounts -= toServiceAccountEmail(googleProject, serviceAccountName)
    serviceAccountKeys -= toServiceAccountEmail(googleProject, serviceAccountName)
    Future.successful(())
  }

  override def addIamRolesForUser(googleProject: GoogleProject, userEmail: WorkbenchEmail, rolesToAdd: Set[String]): Future[Unit] = {
    Future.successful(())
  }

  override def removeIamRolesForUser(googleProject: GoogleProject, userEmail: WorkbenchEmail, rolesToRemove: Set[String]): Future[Unit] = {
    Future.successful(())
  }

  override def addServiceAccountUserRoleForUser(googleProject: GoogleProject, serviceAccountEmail: WorkbenchEmail, userEmail: WorkbenchEmail): Future[Unit] = {
    if (serviceAccounts.contains(serviceAccountEmail)) {
      Future.successful(())
    } else {
      Future.failed(new Exception(s"Unknown service account $userEmail"))
    }
  }

  override def createServiceAccountKey(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail): Future[ServiceAccountKey] = {
    val keyId = ServiceAccountKeyId(UUID.randomUUID().toString)
    val key = ServiceAccountKey(keyId, ServiceAccountPrivateKeyData(Base64.getEncoder.encodeToString(s"abcdefg:${System.currentTimeMillis}${Random.nextLong()}".getBytes(StandardCharsets.UTF_8))), Some(Instant.now), Some(Instant.now.plusSeconds(300)))
    serviceAccountKeys(serviceAccountEmail) += keyId -> key
    Future.successful(key)
  }

  override def removeServiceAccountKey(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail, keyId: ServiceAccountKeyId): Future[Unit] = {
    serviceAccountKeys(serviceAccountEmail) -= keyId
    Future.successful(())
  }

  override def listServiceAccountKeys(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail): Future[Seq[ServiceAccountKey]] = {
    Future.successful(serviceAccountKeys(serviceAccountEmail).values.toSeq)
  }

  override def listUserManagedServiceAccountKeys(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail): Future[Seq[ServiceAccountKey]] = {
    Future.successful(serviceAccountKeys(serviceAccountEmail).values.toSeq)
  }

}
