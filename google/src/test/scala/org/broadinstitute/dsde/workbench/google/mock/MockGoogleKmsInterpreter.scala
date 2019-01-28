package org.broadinstitute.dsde.workbench.google.mock

import cats.effect._
import com.google.cloud.kms.v1.{CryptoKey, KeyRing}
import com.google.iam.v1.Policy
import org.broadinstitute.dsde.workbench.google.GoogleKmsService

/**
  * Mock Google Key Management Service Interpreter
  *
  * created by mtalbott 1/28/19
  */

object MockGoogleKmsInterpreter extends GoogleKmsService[IO] {
  override def createKeyRing(project: String, location: String, keyRingId: String): IO[KeyRing] = IO.pure(KeyRing.newBuilder().build())
  override def getKeyRing(project: String, location: String, keyRingId: String): IO[Option[KeyRing]] = ???
  override def createKey(project: String, location: String, keyRingId: String, keyId: String): IO[CryptoKey] = IO.pure(CryptoKey.newBuilder().build())
  override def getKey(project: String, location: String, keyRingId: String, keyId: String): IO[Option[CryptoKey]] = ???
  override def addMemberToKeyPolicy(project: String, location: String, keyRingId: String, keyId: String, member: String, role: String): IO[Policy] = IO.pure(Policy.newBuilder().build())
  override def removeMemberFromKeyPolicy(project: String, location: String, keyRingId: String, keyId: String, member: String, role: String): IO[Policy] = ???
}