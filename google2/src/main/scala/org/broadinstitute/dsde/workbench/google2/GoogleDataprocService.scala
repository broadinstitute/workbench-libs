package org.broadinstitute.dsde.workbench.google2

import ca.mrvisser.sealerate
import cats.Parallel
import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.mtl.Ask
import cats.syntax.all._
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.services.compute.ComputeScopes
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.compute.v1.Operation
import com.google.cloud.dataproc.v1.{RegionName => _, _}
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}
import org.typelevel.log4cats.StructuredLogger

import scala.collection.JavaConverters._

/**
 * Algebra for Google Dataproc access
 *
 * We follow tagless final pattern similar to https://typelevel.org/cats-tagless/
 */
trait GoogleDataprocService[F[_]] {
  def createCluster(
    project: GoogleProject,
    region: RegionName,
    clusterName: DataprocClusterName,
    createClusterConfig: Option[CreateClusterConfig]
  )(implicit ev: Ask[F, TraceId]): F[Option[DataprocOperation]]

  def stopCluster(project: GoogleProject,
                  region: RegionName,
                  clusterName: DataprocClusterName,
                  metadata: Option[Map[String, String]]
  )(implicit
    ev: Ask[F, TraceId]
  ): F[List[Operation]]

  def startCluster(project: GoogleProject,
                   region: RegionName,
                   clusterName: DataprocClusterName,
                   numPreemptibles: Option[Int],
                   metadata: Option[Map[String, String]]
  )(implicit
    ev: Ask[F, TraceId]
  ): F[List[Operation]]

  def resizeCluster(project: GoogleProject,
                    region: RegionName,
                    clusterName: DataprocClusterName,
                    numWorkers: Option[Int],
                    numPreemptibles: Option[Int]
  )(implicit
    ev: Ask[F, TraceId]
  ): F[Option[DataprocOperation]]

  def deleteCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[DataprocOperation]]

  def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Cluster]]

  def getClusterInstances(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
    ev: Ask[F, TraceId]
  ): F[Map[DataprocRoleZonePreemptibility, Set[InstanceName]]]

  def getClusterError(region: RegionName, operationName: OperationName)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[ClusterError]]
}

object GoogleDataprocService {
  def resource[F[_]: StructuredLogger: Async: Timer: Parallel: ContextShift](
    googleComputeService: GoogleComputeService[F],
    pathToCredential: String,
    blocker: Blocker,
    blockerBound: Semaphore[F],
    supportedRegions: Set[RegionName],
    retryConfig: RetryConfig = RetryPredicates.standardGoogleRetryConfig
  ): Resource[F, GoogleDataprocService[F]] =
    for {
      credential <- credentialResource(pathToCredential)
      scopedCredential = credential.createScoped(Seq(ComputeScopes.CLOUD_PLATFORM).asJava)
      interpreter <- fromCredential(googleComputeService,
                                    scopedCredential,
                                    blocker,
                                    supportedRegions,
                                    blockerBound,
                                    retryConfig
      )
    } yield interpreter

  def resourceFromUserCredential[F[_]: StructuredLogger: Async: Timer: Parallel: ContextShift](
    googleComputeService: GoogleComputeService[F],
    pathToCredential: String,
    blocker: Blocker,
    blockerBound: Semaphore[F],
    supportedRegions: Set[RegionName],
    retryConfig: RetryConfig = RetryPredicates.standardGoogleRetryConfig
  ): Resource[F, GoogleDataprocService[F]] =
    for {
      credential <- userCredentials(pathToCredential)
      scopedCredential = credential.createScoped(Seq(ComputeScopes.CLOUD_PLATFORM).asJava)
      interpreter <- fromCredential(googleComputeService,
                                    scopedCredential,
                                    blocker,
                                    supportedRegions,
                                    blockerBound,
                                    retryConfig
      )
    } yield interpreter

  def fromCredential[F[_]: StructuredLogger: Async: Timer: Parallel: ContextShift](
    googleComputeService: GoogleComputeService[F],
    googleCredentials: GoogleCredentials,
    blocker: Blocker,
    supportedRegions: Set[RegionName],
    blockerBound: Semaphore[F],
    retryConfig: RetryConfig = RetryPredicates.standardGoogleRetryConfig
  ): Resource[F, GoogleDataprocService[F]] = {
    val regionalSettings = supportedRegions.toList.traverse { region =>
      val settings = ClusterControllerSettings
        .newBuilder()
        .setEndpoint(s"${region.value}-dataproc.googleapis.com:443")
        .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
        .build()
      backgroundResourceF(ClusterControllerClient.create(settings)).map(client => region -> client)
    }

    for {
      clients <- regionalSettings
    } yield new GoogleDataprocInterpreter[F](clients.toMap, googleComputeService, blocker, blockerBound, retryConfig)
  }
}

final case class DataprocClusterName(value: String) extends AnyVal

final case class DataprocInstance(name: InstanceName,
                                  project: GoogleProject,
                                  zone: ZoneName,
                                  dataprocRole: DataprocRole
)

final case class CreateClusterConfig(
  gceClusterConfig: GceClusterConfig,
  nodeInitializationActions: List[NodeInitializationAction],
  masterConfig: InstanceGroupConfig,
  workerConfig: Option[InstanceGroupConfig],
  secondaryWorkerConfig: Option[InstanceGroupConfig],
  stagingBucket: GcsBucketName,
  softwareConfig: SoftwareConfig,
  endpointConfig: Option[EndpointConfig]
) //valid properties are https://cloud.google.com/dataproc/docs/concepts/configuring-clusters/cluster-properties

sealed trait DataprocRole extends Product with Serializable
object DataprocRole {
  case object Master extends DataprocRole
  case object Worker extends DataprocRole
  case object SecondaryWorker extends DataprocRole

  val stringToDataprocRole = sealerate.values[DataprocRole].map(p => (p.toString, p)).toMap
}

final case class DataprocRoleZonePreemptibility(role: DataprocRole, zone: ZoneName, isPreemptible: Boolean)

final case class ClusterError(code: Int, message: String)
final case class ClusterErrorDetails(code: Int, message: Option[String])

final case class DataprocOperation(name: OperationName, metadata: ClusterOperationMetadata)
