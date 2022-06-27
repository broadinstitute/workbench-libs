package org.broadinstitute.dsde.workbench.azure

import java.util.UUID

final case class TenantId(value: String) extends AnyVal
final case class SubscriptionId(value: String) extends AnyVal
final case class ManagedResourceGroupName(value: String) extends AnyVal

final case class AzureCloudContext(tenantId: TenantId,
                                   subscriptionId: SubscriptionId,
                                   managedResourceGroupName: ManagedResourceGroupName
) {
  val asString = s"${tenantId.value}/${subscriptionId.value}/${managedResourceGroupName.value}"
}

final case class ClientId(value: String) extends AnyVal
final case class ClientSecret(value: String) extends AnyVal
final case class ManagedAppTenantId(value: String) extends AnyVal

final case class AzureAppRegistrationConfig(clientId: ClientId,
                                            clientSecret: ClientSecret,
                                            managedAppTenantId: ManagedAppTenantId
)

final case class WsmControlledResourceId(value: UUID) extends AnyVal
final case class RelayNamespace(value: String) extends AnyVal
final case class RelayHybridConnectionName(value: String) extends AnyVal
final case class PrimaryKey(value: String) extends AnyVal
