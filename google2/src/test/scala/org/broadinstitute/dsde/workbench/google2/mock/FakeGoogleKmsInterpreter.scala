package org.broadinstitute.dsde.workbench
package google2
package mock

import cats.effect._
import com.google.cloud.kms.v1.{CryptoKey, KeyRing}
import com.google.iam.v1.Policy
import com.google.protobuf.{Duration, Timestamp}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

/**
 * Fake Google Key Management Service Interpreter
 *
 * created by mtalbott 1/28/19
 */
object FakeGoogleKmsInterpreter extends GoogleKmsService[IO] {
  override def createKeyRing(project: GoogleProject, location: Location, keyRingId: KeyRingId): IO[KeyRing] =
    IO.pure(KeyRing.newBuilder().build())
  override def getKeyRing(project: GoogleProject, location: Location, keyRingId: KeyRingId): IO[Option[KeyRing]] = ???
  override def createKey(project: GoogleProject,
                         location: Location,
                         keyRingId: KeyRingId,
                         keyId: KeyId,
                         nextRotationTimeOpt: Option[Timestamp],
                         rotationPeriodOpt: Option[Duration]): IO[CryptoKey] = IO.pure(CryptoKey.newBuilder().build())
  override def getKey(project: GoogleProject,
                      location: Location,
                      keyRingId: KeyRingId,
                      keyId: KeyId): IO[Option[CryptoKey]] = ???
  override def addMemberToKeyPolicy(project: GoogleProject,
                                    location: Location,
                                    keyRingId: KeyRingId,
                                    keyId: KeyId,
                                    member: String,
                                    role: String): IO[Policy] = IO.pure(Policy.newBuilder().build())
  override def removeMemberFromKeyPolicy(project: GoogleProject,
                                         location: Location,
                                         keyRingId: KeyRingId,
                                         keyId: KeyId,
                                         member: String,
                                         role: String): IO[Policy] = ???
}
