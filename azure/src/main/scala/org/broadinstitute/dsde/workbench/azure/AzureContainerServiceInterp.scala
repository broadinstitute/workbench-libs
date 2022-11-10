package org.broadinstitute.dsde.workbench.azure

import cats.effect.Async
import cats.mtl.Ask
import cats.syntax.all._
import com.azure.core.management.AzureEnvironment
import com.azure.core.management.profile.AzureProfile
import com.azure.identity.ClientSecretCredential
import com.azure.resourcemanager.containerservice.ContainerServiceManager
import com.azure.resourcemanager.containerservice.models.KubernetesCluster
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Status
import io.kubernetes.client.proto.V1
import io.kubernetes.client.proto.V1.PodStatus
import io.kubernetes.client.util.KubeConfig
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchException}
import org.broadinstitute.dsde.workbench.util2.{tracedLogging, withLogging}
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

  private def getKubeV1Api(name: AKSClusterName, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[CoreV1Api] =
    for {
      credentials <- getClusterCredentials(name, cloudContext)
      apiClient = new io.kubernetes.client.openapi.ApiClient()
      _ <- F.blocking(apiClient.setApiKey(credentials.token.value))
      _ <- F.blocking(apiClient.setBasePath(credentials.server.value))
      api = new CoreV1Api(apiClient)
    } yield api

  override def listNamespacePods(clusterName: AKSClusterName, namespaceName: String, cloudContext: AzureCloudContext)(
    implicit ev: Ask[F, TraceId]
  ): F[List[io.kubernetes.client.openapi.models.V1Pod]] = for {
    traceId <- ev.ask
    api <- getKubeV1Api(clusterName, cloudContext)
    call =
      F.blocking(
        api.listNamespacedPod(namespaceName, "true", null, null, null, null, null, null, null, null, null)
      )

    response <- withLogging(
      call,
      Some(traceId),
      s"io.kubernetes.client.apis.CoreV1Api.listNamespacedPod(${namespaceName}, true, null, null, null, null, null, null, null, null)"
    )
    pods = response.getItems.asScala.toList
  } yield pods

  override def deleteNamespace(name: AKSClusterName, namespace: String, cloudContext: AzureCloudContext)(implicit
    ev: Ask[F, TraceId]
  ): F[io.kubernetes.client.openapi.models.V1Status] =
    for {
      traceId <- ev.ask
      api <- getKubeV1Api(name, cloudContext)
      call = F.blocking(api.deleteNamespace(namespace, null, null, null, null, null, null))
      res <- withLogging(
        call,
        Some(traceId),
        s"io.kubernetes.client.apis.CoreV1Api.deleteNamespace(${namespace}, null, null, null, null, null, null)"
      )
    } yield res
}
