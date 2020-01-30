package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.mtl.ApplicativeAsk
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.dataproc.v1._
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName

import scala.language.higherKinds

/**
  * Algebra for Google GoogleDataproc access
  *
  * We follow tagless final pattern similar to https://typelevel.org/cats-tagless/
  */
trait GoogleDataprocService[F[_]] {
  def createCluster(region: RegionName, clusterName: ClusterName, createClusterConfig: Option[CreateClusterConfig])
                   (implicit ev: ApplicativeAsk[F, TraceId]): F[CreateClusterResponse]

  def deleteCluster(region: RegionName, clusterName: ClusterName)
                   (implicit ev: ApplicativeAsk[F, TraceId]): F[DeleteClusterResponse]

  def getCluster(region: RegionName, clusterName: ClusterName)
                (implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Cluster]]
}

object GoogleDataprocService {
  def fromCredentialPath[F[_]: Logger: Async: Timer: ContextShift](pathToCredential: String, blocker: Blocker, blockerBound: Semaphore[F], retryConfig: RetryConfig = GoogleDataprocInterpreter.defaultRetryConfig): Resource[F, GoogleDataprocService[F]] = for {
    credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile(pathToCredential)
    client <- fromServiceAccountCrendential(ServiceAccountCredentials.fromStream(credentialFile), blocker, blockerBound, retryConfig)
  } yield client

  def fromServiceAccountCrendential[F[_]: Logger: Async: Timer: ContextShift](serviceAccountCredentials: ServiceAccountCredentials, blocker: Blocker, blockerBound: Semaphore[F], retryConfig: RetryConfig = GoogleTopicAdminInterpreter.defaultRetryConfig): Resource[F, GoogleDataprocService[F]] = {
    val settings = ClusterControllerSettings.newBuilder()
      .setCredentialsProvider(FixedCredentialsProvider.create(serviceAccountCredentials))
      .build()

    for {
     client <- Resource.make(Sync[F].delay(ClusterControllerClient.create(settings)))(c => Sync[F].delay(IO(c.close())))
    } yield new GoogleDataprocInterpreter[F](client, retryConfig, blocker, blockerBound)
  }

  def fromApplicationDefault[F[_]: ContextShift: Timer: Async: Logger](blocker: Blocker, blockerBound: Semaphore[F], retryConfig: RetryConfig = GoogleDataprocInterpreter.defaultRetryConfig): Resource[F, GoogleDataprocService[F]] = for {
    db <- Resource.make(
      Sync[F].delay(
        ClusterControllerClient.create()
      )
    )(c => Sync[F].delay(c.close()))
  } yield new GoogleDataprocInterpreter[F](db, retryConfig, blocker, blockerBound)
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