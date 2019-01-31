package org.broadinstitute.dsde.workbench.google

import com.google.cloud.kms.v1._
import com.google.iam.v1.Policy
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.language.higherKinds

/**
  * Algebra for Google KMS (Key Management Service)
  *
  * created by mtalbott on 1/18/19
  */

trait GoogleKmsService[F[_]] {
  def createKeyRing(project: GoogleProject, location: Location, keyRingId: KeyRingId): F[KeyRing]
  def getKeyRing(project: GoogleProject, location: Location, keyRingId: KeyRingId): F[Option[KeyRing]]
  def createKey(project: GoogleProject, location: Location, keyRingId: KeyRingId, keyId: KeyId): F[CryptoKey]
  def getKey(project: GoogleProject, location: Location, keyRingId: KeyRingId, keyId: KeyId): F[Option[CryptoKey]]
  def getIamPolicy(project: GoogleProject, location: Location, keyRingId: KeyRingId, keyId: KeyId): F[Policy]
  def addMemberToKeyPolicy(project: GoogleProject, location: Location, keyRingId: KeyRingId, keyId: KeyId, member: String, role: String): F[Policy]
  def removeMemberFromKeyPolicy(project: GoogleProject, location: Location, keyRingId: KeyRingId, keyId: KeyId, member: String, role: String): F[Policy]
}

final case class Location(value: String) extends AnyVal
final case class KeyRingId(value: String) extends AnyVal
final case class KeyId(value: String) extends AnyVal
