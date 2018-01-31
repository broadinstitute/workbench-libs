package org.broadinstitute.dsde.workbench.google

import java.time.Instant
import java.util.UUID

import org.broadinstitute.dsde.workbench.model.google.ClusterStatus.ClusterStatus
import org.broadinstitute.dsde.workbench.model.{UserInfo, WorkbenchEmail}
import org.broadinstitute.dsde.workbench.model.google._

import scala.concurrent.Future

trait GoogleDataprocDAO {
  def createCluster(googleProject: GoogleProject, clusterName: ClusterName, clusterConfig: ClusterConfig, initScript: GcsPath, serviceAccountInfo: ClusterServiceAccountInfo): Future[Operation]

  def deleteCluster(googleProject: GoogleProject, clusterName: ClusterName): Future[Unit]

  def getClusterStatus(googleProject: GoogleProject, clusterName: ClusterName): Future[ClusterStatus]

  def listClusters(googleProject: GoogleProject): Future[List[UUID]]

  def getClusterMasterInstanceIp(googleProject: GoogleProject, clusterName: ClusterName): Future[Option[IP]]

  def getClusterErrorDetails(operationName: OperationName): Future[Option[ClusterErrorDetails]]

  def updateFirewallRule(googleProject: GoogleProject, firewallRule: FirewallRule): Future[Unit]

  def getUserInfoAndExpirationFromAccessToken(accessToken: String): Future[(UserInfo, Instant)]

  def getComputeEngineDefaultServiceAccount(googleProject: GoogleProject): Future[Option[WorkbenchEmail]]
}