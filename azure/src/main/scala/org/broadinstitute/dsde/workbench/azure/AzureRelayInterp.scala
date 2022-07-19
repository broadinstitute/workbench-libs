package org.broadinstitute.dsde.workbench.azure

import cats.effect.Async
import cats.implicits._
import cats.mtl.Ask
import com.azure.core.management.AzureEnvironment
import com.azure.core.management.exception.ManagementException
import com.azure.core.management.profile.AzureProfile
import com.azure.identity.ClientSecretCredential
import com.azure.resourcemanager.relay.RelayManager
import com.azure.resourcemanager.relay.fluent.models.AuthorizationRuleInner
import com.azure.resourcemanager.relay.models.AccessRights
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.tracedLogging
import org.typelevel.log4cats.StructuredLogger

import scala.jdk.CollectionConverters._

class AzureRelayInterp[F[_]](clientSecretCredential: ClientSecretCredential)(implicit
  val F: Async[F],
  logger: StructuredLogger[F]
) extends AzureRelayService[F] {
  def createRelayHybridConnection(relayNamespace: RelayNamespace,
                                  hybridConnectionName: RelayHybridConnectionName,
                                  cloudContext: AzureCloudContext
  )(implicit
    ev: Ask[F, TraceId]
  ): F[PrimaryKey] = for {
    manager <- buildRelayManager(cloudContext)
    fa1 = F
      .delay(
        manager
          .hybridConnections()
          .define(hybridConnectionName.value)
          .withExistingNamespace(cloudContext.managedResourceGroupName.value, relayNamespace.value)
          .withRequiresClientAuthorization(false)
          .create()
      )
      .void
      .handleErrorWith {
        case e: ManagementException if e.getValue.getCode().equals("Conflict") =>
          logger.info(s"${hybridConnectionName} already exists in ${cloudContext}")
        case e => F.raiseError[Unit](e)
      }
    _ <- tracedLogging(
      fa1,
      s"com.azure.resourcemanager.relay.models.HybridConnection.DefinitionStages.WithCreate.create(${cloudContext.managedResourceGroupName.value}, ${relayNamespace.value}, ${hybridConnectionName.value})"
    )
    fa2 = F
      .delay(
        manager
          .hybridConnections()
          .createOrUpdateAuthorizationRule(
            cloudContext.managedResourceGroupName.value,
            relayNamespace.value,
            hybridConnectionName.value,
            "listener",
            new AuthorizationRuleInner().withRights(List(AccessRights.LISTEN).asJava)
          )
      )
    _ <- tracedLogging(
      fa2,
      s"com.azure.resourcemanager.relay.models.HybridConnections.createOrUpdateAuthorizationRule(${cloudContext.managedResourceGroupName.value}, ${relayNamespace.value}, ${hybridConnectionName.value})"
    )
    fa3 = F
      .delay(
        manager
          .hybridConnections()
          .listKeys(cloudContext.managedResourceGroupName.value,
                    relayNamespace.value,
                    hybridConnectionName.value,
                    "listener"
          )
          .primaryKey()
      )
    key <- tracedLogging(
      fa3,
      s"com.azure.resourcemanager.relay.models.HybridConnections.listKeys(${cloudContext.managedResourceGroupName.value}, ${relayNamespace.value}, ${hybridConnectionName.value})"
    )
  } yield PrimaryKey(key)

  def deleteRelayHybridConnection(relayNamespace: RelayNamespace,
                                  hybridConnectionName: RelayHybridConnectionName,
                                  cloudContext: AzureCloudContext
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Unit] = for {
    manager <- buildRelayManager(cloudContext)
    fa = F
      .delay(
        manager
          .hybridConnections()
          .delete(cloudContext.managedResourceGroupName.value, relayNamespace.value, hybridConnectionName.value)
      )
    _ <- tracedLogging(
      fa,
      s"com.azure.resourcemanager.relay.models.HybridConnections.delete(${cloudContext.managedResourceGroupName.value}, ${relayNamespace.value}, ${hybridConnectionName.value})"
    )
  } yield ()

  private def buildRelayManager(azureCloudContext: AzureCloudContext): F[RelayManager] = {
    val azureProfile =
      new AzureProfile(azureCloudContext.tenantId.value, azureCloudContext.subscriptionId.value, AzureEnvironment.AZURE)

    F.delay(RelayManager.authenticate(clientSecretCredential, azureProfile))
  }
}
