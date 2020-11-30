package org.broadinstitute.dsde.workbench.google

import cats.syntax.all._
import cats.effect.{ContextShift, Resource, Sync}
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.kms.v1.CryptoKey.CryptoKeyPurpose
import com.google.cloud.kms.v1._
import com.google.iam.v1.{Binding, Policy}
import com.google.protobuf.{Duration, Timestamp}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/**
 * Interpreter for Google KMS (Key Management Service) algebra
 *
 * created by mtalbott on 1/18/19
 */
private[google] class GoogleKmsInterpreter[F[_]: Sync: ContextShift](client: KeyManagementServiceClient,
                                                                     blockingEc: ExecutionContext
) extends GoogleKmsService[F] {
  override def createKeyRing(project: GoogleProject, location: Location, keyRingId: KeyRingId): F[KeyRing] = {
    val locationName = LocationName.format(project.value, location.value)
    blockingF(Sync[F].delay[KeyRing] {
      client.createKeyRing(locationName, keyRingId.value, KeyRing.newBuilder().build())
    })
  }

  override def getKeyRing(project: GoogleProject, location: Location, keyRingId: KeyRingId): F[Option[KeyRing]] = {
    val keyRingName = KeyRingName.format(project.value, location.value, keyRingId.value)
    blockingF(Sync[F].delay[Option[KeyRing]] {
      Option(client.getKeyRing(keyRingName))
    })
  }

  override def createKey(project: GoogleProject,
                         location: Location,
                         keyRingId: KeyRingId,
                         keyId: KeyId,
                         nextRotationTimeOpt: Option[Timestamp] = None,
                         rotationPeriodOpt: Option[Duration] = None
  ): F[CryptoKey] = {
    val keyRingName = KeyRingName.format(project.value, location.value, keyRingId.value)
    (nextRotationTimeOpt, rotationPeriodOpt) match {
      case (Some(nextRotationTime), Some(rotationPeriod)) =>
        blockingF(Sync[F].delay[CryptoKey] {
          client.createCryptoKey(
            keyRingName,
            keyId.value,
            CryptoKey
              .newBuilder()
              .setPurpose(CryptoKeyPurpose.ENCRYPT_DECRYPT)
              .setNextRotationTime(nextRotationTime)
              .setRotationPeriod(rotationPeriod)
              .build()
          )
        })
      case (_, _) =>
        blockingF(Sync[F].delay[CryptoKey] {
          client.createCryptoKey(keyRingName,
                                 keyId.value,
                                 CryptoKey
                                   .newBuilder()
                                   .setPurpose(CryptoKeyPurpose.ENCRYPT_DECRYPT)
                                   .build()
          )
        })
    }
  }

  override def getKey(project: GoogleProject,
                      location: Location,
                      keyRingId: KeyRingId,
                      keyId: KeyId
  ): F[Option[CryptoKey]] = {
    val keyName = CryptoKeyName.format(project.value, location.value, keyRingId.value, keyId.value)
    blockingF(Sync[F].delay[Option[CryptoKey]] {
      Option(client.getCryptoKey(keyName))
    })
  }

  override def addMemberToKeyPolicy(project: GoogleProject,
                                    location: Location,
                                    keyRingId: KeyRingId,
                                    keyId: KeyId,
                                    member: String,
                                    role: String
  ): F[Policy] =
    for {
      currentIamPolicy <- getIamPolicy(project, location, keyRingId, keyId)

      newBinding = Binding
        .newBuilder()
        .setRole(role)
        .addMembers(member)
        .build()
      newPolicy = Policy
        .newBuilder(currentIamPolicy)
        .addBindings(newBinding)
        .build()

      _ <- setIamPolicy(project, location, keyRingId, keyId, newPolicy)
    } yield newPolicy

  override def removeMemberFromKeyPolicy(project: GoogleProject,
                                         location: Location,
                                         keyRingId: KeyRingId,
                                         keyId: KeyId,
                                         member: String,
                                         role: String
  ): F[Policy] =
    for {
      currentIamPolicy <- getIamPolicy(project, location, keyRingId, keyId)

      otherBindings = currentIamPolicy.getBindingsList.asScala.toList.filter(binding => !binding.getRole.equals(role))
      newBindings = currentIamPolicy.getBindingsList.asScala.toList
        .filter(binding => binding.getRole.equals(role))
        .map { binding =>
          Binding
            .newBuilder()
            .setRole(binding.getRole)
            .addAllMembers(binding.getMembersList.asScala.filter(!_.equals(member)).asJava)
            .build()
        }

      newPolicy = Policy
        .newBuilder()
        .addAllBindings((otherBindings ++ newBindings).asJava)
        .build()

      _ <- setIamPolicy(project, location, keyRingId, keyId, newPolicy)
    } yield newPolicy

  private def getIamPolicy(project: GoogleProject,
                           location: Location,
                           keyRingId: KeyRingId,
                           keyId: KeyId
  ): F[Policy] = {
    val keyName = CryptoKeyName.format(project.value, location.value, keyRingId.value, keyId.value)
    blockingF(Sync[F].delay[Policy] {
      client.getIamPolicy(keyName)
    })
  }

  private def setIamPolicy(project: GoogleProject,
                           location: Location,
                           keyRingId: KeyRingId,
                           keyId: KeyId,
                           newPolicy: Policy
  ): F[Policy] = {
    val keyName = CryptoKeyName.format(project.value, location.value, keyRingId.value, keyId.value)
    blockingF(Sync[F].delay[Policy] {
      client.setIamPolicy(keyName, newPolicy)
    })
  }

  private def blockingF[A](fa: F[A]): F[A] = ContextShift[F].evalOn(blockingEc)(fa)
}

object GoogleKmsInterpreter {
  def apply[F[_]: Sync: ContextShift](client: KeyManagementServiceClient,
                                      blockingEc: ExecutionContext
  ): GoogleKmsInterpreter[F] =
    new GoogleKmsInterpreter[F](client, blockingEc)

  def client[F[_]: Sync](pathToJson: String): Resource[F, KeyManagementServiceClient] =
    for {
      credentials <- org.broadinstitute.dsde.workbench.util2.readFile(pathToJson)
      client <- Resource.make[F, KeyManagementServiceClient](
        Sync[F].delay(
          KeyManagementServiceClient.create(
            KeyManagementServiceSettings
              .newBuilder()
              .setCredentialsProvider(
                FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(credentials))
              )
              .build()
          )
        )
      )(client => Sync[F].delay(client.close()))
    } yield client
}
