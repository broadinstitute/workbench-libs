package org.broadinstitute.dsde.workbench.google

import cats.effect.{Resource, Sync}
import cats.syntax.all._
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.kms.v1.CryptoKey.CryptoKeyPurpose
import com.google.cloud.kms.v1._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.iam.v1.{Binding, Policy}
import com.google.protobuf.{Duration, Timestamp}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.jdk.CollectionConverters._
import scala.language.higherKinds

/**
 * Interpreter for Google KMS (Key Management Service) algebra
 *
 * created by mtalbott on 1/18/19
 */
private[google] class GoogleKmsInterpreter[F[_]: Sync](client: KeyManagementServiceClient) extends GoogleKmsService[F] {
  override def createKeyRing(project: GoogleProject, location: Location, keyRingId: KeyRingId): F[KeyRing] = {
    val locationName = LocationName.format(project.value, location.value)
    Sync[F].blocking[KeyRing] {
      client.createKeyRing(locationName, keyRingId.value, KeyRing.newBuilder().build())
    }
  }

  override def getKeyRing(project: GoogleProject, location: Location, keyRingId: KeyRingId): F[Option[KeyRing]] = {
    val keyRingName = KeyRingName.format(project.value, location.value, keyRingId.value)
    Sync[F].blocking[Option[KeyRing]] {
      Option(client.getKeyRing(keyRingName))
    }
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
        Sync[F].blocking[CryptoKey] {
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
        }
      case (_, _) =>
        Sync[F].blocking[CryptoKey] {
          client.createCryptoKey(keyRingName,
                                 keyId.value,
                                 CryptoKey
                                   .newBuilder()
                                   .setPurpose(CryptoKeyPurpose.ENCRYPT_DECRYPT)
                                   .build()
          )
        }
    }
  }

  override def getKey(project: GoogleProject,
                      location: Location,
                      keyRingId: KeyRingId,
                      keyId: KeyId
  ): F[Option[CryptoKey]] = {
    val keyName = CryptoKeyName.format(project.value, location.value, keyRingId.value, keyId.value)
    Sync[F].blocking[Option[CryptoKey]] {
      Option(client.getCryptoKey(keyName))
    }
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
    Sync[F].blocking[Policy] {
      client.getIamPolicy(keyName)
    }
  }

  private def setIamPolicy(project: GoogleProject,
                           location: Location,
                           keyRingId: KeyRingId,
                           keyId: KeyId,
                           newPolicy: Policy
  ): F[Policy] = {
    val keyName = CryptoKeyName.format(project.value, location.value, keyRingId.value, keyId.value)
    Sync[F].blocking[Policy] {
      client.setIamPolicy(keyName, newPolicy)
    }
  }
}

object GoogleKmsInterpreter {
  def apply[F[_]: Sync](client: KeyManagementServiceClient): GoogleKmsInterpreter[F] =
    new GoogleKmsInterpreter[F](client)

  def client[F[_]: Sync](pathToJson: String): Resource[F, KeyManagementServiceClient] = {
    val executorProviderBuilder = KeyManagementServiceSettings.defaultExecutorProviderBuilder()
    val threadFactory = new ThreadFactoryBuilder()
      .setThreadFactory(executorProviderBuilder.getThreadFactory)
      .setNameFormat("goog-kms-%d")
      .build()
    val executorProvider = executorProviderBuilder.setThreadFactory(threadFactory).build()
    val transportProvider =
      KeyManagementServiceSettings.defaultTransportChannelProvider().withExecutor(executorProvider.getExecutor)

    for {
      credentials <- org.broadinstitute.dsde.workbench.util2.readFile(pathToJson)
      client <- Resource.make[F, KeyManagementServiceClient](
        Sync[F].delay(
          KeyManagementServiceClient.create(
            KeyManagementServiceSettings
              .newBuilder()
              .setBackgroundExecutorProvider(executorProvider)
              .setCredentialsProvider(
                FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(credentials))
              )
              .setTransportChannelProvider(transportProvider)
              .build()
          )
        )
      )(client => Sync[F].delay(client.close()))
    } yield client
  }
}
