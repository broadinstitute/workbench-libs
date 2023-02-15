package org.broadinstitute.dsde.workbench.azure

import cats.effect.Async
import cats.mtl.Ask
import cats.syntax.all._
import com.azure.core.management.AzureEnvironment
import com.azure.core.management.exception.ManagementException
import com.azure.core.management.profile.AzureProfile
import com.azure.identity.ClientSecretCredential
import com.azure.resourcemanager.containerservice.ContainerServiceManager
import com.azure.resourcemanager.containerservice.models.KubernetesCluster
import io.kubernetes.client.util.KubeConfig
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchException}
import org.broadinstitute.dsde.workbench.util2.tracedLogging
import org.typelevel.log4cats.StructuredLogger

import java.io.{ByteArrayInputStream, InputStreamReader}
import scala.jdk.CollectionConverters._

class AzureContainerServiceInterp[F[_]](clientSecretCredential: ClientSecretCredential)(implicit
  val F: Async[F],
  logger: StructuredLogger[F]
) extends AzureContainerService[F] {

  override def getCluster(name: AKSClusterName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[KubernetesCluster] =
    for {
      mgr <- buildContainerServiceManager(cloudContext)
      resp <- tracedLogging(
        F.delay(
          mgr
            .kubernetesClusters()
            .getByResourceGroup(cloudContext.managedResourceGroupName.value, name.value)
        ),
        s"com.azure.resourcemanager.resources.fluentcore.arm.collection,getByResourceGroup(${cloudContext.managedResourceGroupName.value}, ${name.value})"
      )
    } yield resp

  override def listClusters(cloudContext: AzureCloudContext)(implicit ev: Ask[F, TraceId]): F[List[KubernetesCluster]] =
    for {
      mgr <- buildContainerServiceManager(cloudContext)
      fa =
        F.delay(
          mgr
            .kubernetesClusters()
            .listByResourceGroup(cloudContext.managedResourceGroupName.value)
        ).map(_.asScala.toList)
          .handleErrorWith {
            case e: ManagementException
                if e.getValue
                  .getCode()
                  .equals("ResourceNotFound") | e.getValue.getCode().equals("AuthorizationFailed") =>
              F.pure(List.empty)
            case e => F.raiseError(e)
          }
      res <- tracedLogging(
        fa,
        s"com.azure.resourcemanager.resources.fluentcore.arm.collection,listByResourceGroup(${cloudContext.managedResourceGroupName.value})"
      )
    } yield res

  override def getClusterCredentials(name: AKSClusterName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[AKSCredentials] =
    for {
      mgr <- buildContainerServiceManager(cloudContext)
      resp <- tracedLogging(
        F.delay(
          mgr
            .kubernetesClusters()
            .manager()
            .serviceClient()
            .getManagedClusters()
            .listClusterUserCredentials(cloudContext.managedResourceGroupName.value, name.value)
        ),
        s"com.azure.resourcemanager.containerservice.fluent.ManagedClustersClient.listClusterUserCredentials(${cloudContext.managedResourceGroupName.value}, ${name.value})"
      )
      kubeConfig <- F.fromOption(resp.kubeconfigs().asScala.headOption, new WorkbenchException("No AKS credential"))
      // Parse the kubeconfig file
      parsedKubeConfig <- F.delay(
        KubeConfig.loadKubeConfig(new InputStreamReader(new ByteArrayInputStream(kubeConfig.value)))
      )
      // Null-check fields from the Java API
      server <- F.fromOption(Option(parsedKubeConfig.getServer).map(AKSServer), new WorkbenchException("No AKS server"))
      userToken <- F.fromOption(parsedKubeConfig.getCredentials.asScala.get("token").map(AKSToken),
                                new WorkbenchException("No AKS token")
      )
      certificate <- F.fromOption(Option(parsedKubeConfig.getCertificateAuthorityData).map(AKSCertificate),
                                  new WorkbenchException("No AKS certificate")
      )

    } yield AKSCredentials(server, userToken, certificate)

  private def buildContainerServiceManager(cloudContext: AzureCloudContext): F[ContainerServiceManager] = {
    val azureProfile =
      new AzureProfile(cloudContext.tenantId.value, cloudContext.subscriptionId.value, AzureEnvironment.AZURE)
    F.delay(ContainerServiceManager.authenticate(clientSecretCredential, azureProfile))
  }
}
