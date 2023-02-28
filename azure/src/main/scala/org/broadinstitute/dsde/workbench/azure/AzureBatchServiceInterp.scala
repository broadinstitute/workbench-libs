package org.broadinstitute.dsde.workbench.azure

import cats.effect.Async
import cats.mtl.Ask
import cats.syntax.all._
import com.azure.core.management.AzureEnvironment
import com.azure.core.management.profile.AzureProfile
import com.azure.identity.ClientSecretCredential
import com.azure.resourcemanager.batch.BatchManager
import com.azure.resourcemanager.batch.models.BatchAccount
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.tracedLogging
import org.typelevel.log4cats.StructuredLogger

class AzureBatchServiceInterp[F[_]](clientSecretCredential: ClientSecretCredential)(implicit
  val F: Async[F],
  logger: StructuredLogger[F]
) extends AzureBatchService[F] {

  override def getBatchAccount(name: BatchAccountName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[BatchAccount] =
    for {
      mgr <- buildBatchManager(cloudContext)
      resp <- tracedLogging(
        F.delay(
          mgr.batchAccounts().getByResourceGroup(cloudContext.managedResourceGroupName.value, name.value)
        ),
        s"com.azure.resourcemanager.applicationinsights,getByResourceGroup(${cloudContext.managedResourceGroupName.value}, ${name.value})"
      )
    } yield resp

  private def buildBatchManager(cloudContext: AzureCloudContext): F[BatchManager] = {
    val azureProfile =
      new AzureProfile(cloudContext.tenantId.value, cloudContext.subscriptionId.value, AzureEnvironment.AZURE)
    F.delay(BatchManager.authenticate(clientSecretCredential, azureProfile))
  }
}

