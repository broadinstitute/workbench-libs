package org.broadinstitute.dsde.workbench.google2

import java.util.UUID

import cats.effect._
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.dataproc.v1._
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/**
  * Algebra for Google firestore access
  *
  * We follow tagless final pattern similar to https://typelevel.org/cats-tagless/
  */
trait GoogleDataproc[F[_]] {
  def createCluster(region: RegionName, clusterName: ClusterName, createClusterConfig: Option[CreateClusterConfig]): F[CreateClusterResponse]

  def deleteCluster(region: RegionName, clusterName: ClusterName): F[DeleteClusterResponse]

  def getCluster(region: RegionName, clusterName: ClusterName): F[Option[Cluster]]

  def listClusters(region: RegionName): F[List[UUID]]
}

object GoogleDataproc {
  def fromCredentialPath[F[_]: Logger: Async: Timer: ContextShift](pathToCredential: String, retryConfig: RetryConfig = GoogleTopicAdminInterpreter.defaultRetryConfig, blockingExecutionContext: ExecutionContext): Resource[F, GoogleDataproc[F]] = for {
    credentialFile <- org.broadinstitute.dsde.workbench.util.readFile(pathToCredential)
    client <- fromServiceAccountCrendential(ServiceAccountCredentials.fromStream(credentialFile), retryConfig, blockingExecutionContext)
  } yield client

  def fromServiceAccountCrendential[F[_]: Logger: Async: Timer: ContextShift](serviceAccountCredentials: ServiceAccountCredentials, retryConfig: RetryConfig = GoogleTopicAdminInterpreter.defaultRetryConfig, blockingExecutionContext: ExecutionContext): Resource[F, GoogleDataproc[F]] = {
    val settings = ClusterControllerSettings.newBuilder()
      .setCredentialsProvider(FixedCredentialsProvider.create(serviceAccountCredentials))
      .build()

    for {
     client <- Resource.make(Sync[F].delay(ClusterControllerClient.create(settings)))(c => Sync[F].delay(IO(c.shutdown())))
    } yield new GoogleDataprocInterpreter[F](client, retryConfig, blockingExecutionContext)
  }
}

final case class ClusterName(asString: String) extends AnyVal
final case class ClusterErrorDetails(code: Int, message: Option[String])

sealed abstract class CreateClusterResponse
object CreateClusterResponse {
  final case class Success(clusterOperationMetadata: ClusterOperationMetadata) extends CreateClusterResponse
  case object AlreadyExists extends CreateClusterResponse
}

final case class CreateClusterConfig(gceClusterConfig: GceClusterConfig,
                                     nodeInitializationAction: NodeInitializationAction,
                                     instanceGroupConfig: InstanceGroupConfig,
                                     stagingBucket: GcsBucketName,
                                     softwareConfig: SoftwareConfig) //valid properties are https://cloud.google.com/dataproc/docs/concepts/configuring-clusters/cluster-properties

sealed abstract class DeleteClusterResponse
object DeleteClusterResponse {
  final case class Success(clusterOperationMetadata: ClusterOperationMetadata) extends DeleteClusterResponse
  case object NotFound extends DeleteClusterResponse
}