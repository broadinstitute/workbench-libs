# Testing Locally in sbt console

This is an example on how to test Azure methods locally. In this example, we are testing the
AzureContainerServiceInterp.getClusterCredentials method.

Start sbt console:
```
sbt "project workbenchAzure" console
```

Set up imports. Type `:paste` to get a copy-paste editor:
```scala
import cats.effect.IO
import org.broadinstitute.dsde.workbench.azure._
import org.broadinstitute.dsde.workbench.azure.AzureContainerService._
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._
import org.broadinstitute.dsde.workbench.model.TraceId
import cats.mtl.Ask
import java.util.UUID
import cats.effect.unsafe.implicits.global

implicit val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger
implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

// Replace with your tenant/subscription/MRG
val cloudContext = AzureCloudContext(
  TenantId("0cb7a640-45a2-4ed6-be9f-63519f86e04b"), 
  SubscriptionId("3efc5bdf-be0e-44e7-b1d7-c08931e3c16c"), 
  ManagedResourceGroupName("mrg-rt-aks-end-to-end-20220930090032"))

// Get from vault
val config = AzureAppRegistrationConfig(
  ClientId("<client>>"), 
  ClientSecret("<secret>"), 
  ManagedAppTenantId("<tenant>"))

val clusterName = AKSClusterName("lz961d62cced17ccac037ce22")
```

Create AzureContainerService resource.
```scala
val service = AzureContainerService.fromAzureAppRegistrationConfig[IO](config)
```

Call methods on the AzureContainerService object.
```scala
service.use(x => x.getClusterCredentials(clusterName, cloudContext)).unsafeRunSync
```
