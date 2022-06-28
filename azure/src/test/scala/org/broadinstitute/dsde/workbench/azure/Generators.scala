package org.broadinstitute.dsde.workbench.azure

import org.scalacheck.Gen

class Generators {
  val genTenantId = Gen.uuid.map(x => TenantId(x.toString))
  val genSubscriptionId = Gen.uuid.map(x => SubscriptionId(x.toString))
  val genMrg = Gen.hexStr.map(s => ManagedResourceGroupName(s"mrg-${s}"))
  val genAzureCloudContext = for {
    tenantId <- genTenantId
    subscriptionId <- genSubscriptionId
    mrg <- genMrg
  } yield AzureCloudContext(tenantId, subscriptionId, mrg)
}
