package org.broadinstitute.dsde.workbench.google

import com.google.cloud.kms.v1._
import com.google.iam.v1.Policy

import scala.language.higherKinds

/**
  * Algebra for Google KMS (Key Management Service)
  *
  * created by mtalbott on 1/18/19
  */

trait GoogleKmsService[F[_]] {
  def createKeyRing(project: String, location: String, keyRingId: String): F[KeyRing]
  def getKeyRing(project: String, location: String, keyRingId: String): F[Option[KeyRing]]
  def createKey(project: String, location: String, keyRingId: String, keyId: String): F[CryptoKey]
  def getKey(project: String, location: String, keyRingId: String, keyId: String): F[Option[CryptoKey]]
  def addMemberToKeyPolicy(project: String, location: String, keyRingId: String, keyId: String, member: String, role: String): F[Policy]
  def removeMemberFromKeyPolicy(project: String, location: String, keyRingId: String, keyId: String, member: String, role: String): F[Policy]
}


