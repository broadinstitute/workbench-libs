package org.broadinstitute.dsde.workbench.azure

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
