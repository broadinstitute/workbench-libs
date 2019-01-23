package org.broadinstitute.dsde.workbench.google

import cats.effect.Sync
import com.google.cloud.kms.v1.CryptoKey.CryptoKeyPurpose
import com.google.cloud.kms.v1._
import com.google.iam.v1.{Binding, Policy}
import scala.collection.JavaConverters._

import scala.language.higherKinds

/**
  * Interpreter for Google KMS (Key Management Service) algebra
  *
  * created by mtalbott on 1/18/19
  */

private[google] class GoogleKmsInterpreter[F[_]: Sync](client: KeyManagementServiceClient) extends GoogleKmsService[F] {
  override def createKeyRing(project: String, location: String, keyRingId: String): F[KeyRing] = {
    val locationName = LocationName.format(project, location)
    Sync[F].delay[KeyRing] {
      client.createKeyRing(locationName, keyRingId, KeyRing.newBuilder().build())
    }
  }

  override def getKeyRing(project: String, location: String, keyRingId: String): F[Option[KeyRing]] = {
    val keyRingName = KeyRingName.format(project, location, keyRingId)
    Sync[F].delay[Option[KeyRing]] {
      Option(client.getKeyRing(keyRingName))
    }
  }

  override def createKey(project: String, location: String, keyRingId: String, keyId: String): F[CryptoKey] = {
    val keyRingName = KeyRingName.format(project, location, keyRingId)
    Sync[F].delay[CryptoKey] {
      client.createCryptoKey(keyRingName, keyId, CryptoKey.newBuilder()
        .setPurpose(CryptoKeyPurpose.ENCRYPT_DECRYPT)
        .build())
    }
  }

  override def getKey(project: String, location: String, keyRingId: String, keyId: String): F[Option[CryptoKey]] = {
    val keyName = CryptoKeyName.format(project, location, keyRingId, keyId)
    Sync[F].delay[Option[CryptoKey]] {
      Option(client.getCryptoKey(keyName))
    }
  }

  override def getIamPolicy(project: String, location: String, keyRingId: String, keyId: String): F[Option[Policy]] = {
    val keyName = CryptoKeyName.format(project, location, keyRingId, keyId)
    Sync[F].delay[Option[Policy]] {
      Option(client.getIamPolicy(keyName))
    }
  }

  override def setIamPolicy(project: String, location: String, keyRingId: String, keyId: String, newPolicy: Policy): F[Policy] = {
    val keyName = CryptoKeyName.format(project, location, keyRingId, keyId)
    Sync[F].delay[Policy] {
      client.setIamPolicy(keyName, newPolicy)
    }
  }

  override def addMemberToKeyPolicy(project: String, location: String, keyRingId: String, keyId: String, member: String, role: String): F[Policy] = {
    for {
      currentIamPolicy <- getIamPolicy(project, location, keyRingId, keyId)

      newBinding = Binding.newBuilder()
        .setRole(role)
        .addMembers(member)
        .build()
      newPolicy = Policy.newBuilder(currentIamPolicy)
        .addBindings(newBinding)
        .build()

      _ <- setIamPolicy(project, location, keyRingId, keyId, newPolicy)
    } yield newPolicy
  }

  override def removeMemberFromKeyPolicy(project: String, location: String, keyRingId: String, keyId: String, member: String, role: String): F[Policy] =
    for {
      currentIamPolicyOpt: Option[Policy] <- getIamPolicy(project, location, keyRingId, keyId)
      currentIamPolicy: Policy <- currentIamPolicyOpt

      otherBindings = currentIamPolicy.getBindingsList.asScala.toList.filter(!_.getRole.equals(role))
      newBindings = currentIamPolicy.getBindingsList.asScala.toList.filter(_.getRole.equals(role)).map { binding =>
        Binding.newBuilder().setRole(binding.getRole())
          .addAllMembers(binding.getMembersList.asScala.filter(!_.equals(member)).asJava)
          .build()
      }

      newPolicy = Policy.newBuilder()
        .addAllBindings((otherBindings ++ newBindings).asJava)
        .build()

      _ <- setIamPolicy(project, location, keyRingId, keyId, newPolicy)
    } yield newPolicy
}