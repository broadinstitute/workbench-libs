package org.broadinstitute.dsde.workbench.model.google

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

import org.broadinstitute.dsde.workbench.model._
import spray.json.{JsString, JsValue, RootJsonFormat}

// Projects
case class GoogleProject(value: String) extends ValueObject

// Service Accounts
case class ServiceAccount(subjectId: ServiceAccountSubjectId, email: WorkbenchEmail, displayName: ServiceAccountDisplayName)
case class ServiceAccountSubjectId(value: String) extends ValueObject //The SA's Subject ID.
case class ServiceAccountName(value: String) extends ValueObject //The left half of the SA's email.
case class ServiceAccountDisplayName(value: String) extends ValueObject //A friendly name.

case class ServiceAccountKeyId(value: String) extends ValueObject
case class ServiceAccountPrivateKeyData(value: String) extends ValueObject with Base64Support
case class ServiceAccountKey(id: ServiceAccountKeyId, privateKeyData: ServiceAccountPrivateKeyData, validAfter: Option[Instant], validBefore: Option[Instant])

// Storage
case class GcsBucketName(value: String) extends ValueObject
case class GcsObjectName(value: String) extends ValueObject
case class GcsPath(bucketName: GcsBucketName, objectName: GcsObjectName)
case class GcsParseError(value: String) extends ValueObject

sealed trait GcsRole extends ValueObject
case object Reader extends GcsRole { val value = "READER" }
case object Writer extends GcsRole { val value = "WRITER" }
case object Owner extends GcsRole { val value = "OWNER" }

case class GcsAccessControl(email: WorkbenchEmail, permission: GcsRole)

// Dataproc
case class ClusterName(value: String) extends ValueObject
case class InstanceName(value: String) extends ValueObject
case class ZoneUri(value: String) extends ValueObject

case class ClusterConfig(numberOfWorkers: Option[Int] = None,
                         masterMachineType: Option[String] = None,
                         masterDiskSize: Option[Int] = None,  //min 10
                         workerMachineType: Option[String] = None,
                         workerDiskSize: Option[Int] = None,   //min 10
                         numberOfWorkerLocalSSDs: Option[Int] = None, //min 0 max 8
                         numberOfPreemptibleWorkers: Option[Int] = None)

case class OperationName(value: String) extends ValueObject
case class Operation(name: OperationName, uuid: UUID)

case class ClusterServiceAccountInfo(clusterServiceAccount: Option[WorkbenchEmail], notebookServiceAccount: Option[WorkbenchEmail])
case class ClusterErrorDetails(code: Int, message: Option[String])

object ClusterStatus extends Enumeration {
  type ClusterStatus = Value
  //NOTE: Remember to update the definition of this enum in Swagger when you add new ones
  val Unknown, Creating, Running, Updating, Error, Deleting, Deleted = Value

  val activeStatuses = Set(Unknown, Creating, Running, Updating)
  val deletableStatuses = Set(Unknown, Creating, Running, Updating, Error)
  val monitoredStatuses = Set(Unknown, Creating, Updating, Deleting)

  def withNameOpt(s: String): Option[ClusterStatus] = values.find(_.toString == s)

  def withNameIgnoreCase(str: String): ClusterStatus = {
    values.find(_.toString.equalsIgnoreCase(str)).getOrElse(throw new IllegalArgumentException(s"Unknown cluster status: $str"))
  }
}

// VPC network
case class IP(value: String) extends ValueObject
case class NetworkTag(value: String) extends ValueObject
case class FirewallRuleName(value: String) extends ValueObject
case class FirewallRulePort(value: String) extends ValueObject
case class FirewallRuleNetwork(value: String) extends ValueObject
case class FirewallRule(name: FirewallRuleName, protocol: String = "tcp", ports: List[FirewallRulePort], network: FirewallRuleNetwork, targetTags: List[NetworkTag])

object GoogleModelJsonSupport {
  import spray.json.DefaultJsonProtocol._
  import WorkbenchIdentityJsonSupport.WorkbenchEmailFormat

  implicit object InstantFormat extends RootJsonFormat[Instant] {
    def write(instant: Instant): JsString = {
      JsString(DateTimeFormatter.ISO_INSTANT.format(instant))
    }

    def read(value: JsValue): Instant = value match {
      case JsString(str) => Instant.from(DateTimeFormatter.ISO_INSTANT.parse(str))
      case _ => throw new WorkbenchException(s"Unable to unmarshal Instant from $value")
    }
  }

  implicit val GoogleProjectFormat = ValueObjectFormat(GoogleProject)

  implicit val ServiceAccountUniqueIdFormat = ValueObjectFormat(ServiceAccountSubjectId)
  implicit val ServiceAccountNameFormat = ValueObjectFormat(ServiceAccountName)
  implicit val ServiceAccountDisplayNameFormat = ValueObjectFormat(ServiceAccountDisplayName)
  implicit val ServiceAccountFormat = jsonFormat3(ServiceAccount)

  implicit val ServiceAccountKeyIdFormat = ValueObjectFormat(ServiceAccountKeyId)
  implicit val ServiceAccountPrivateKeyDataFormat = ValueObjectFormat(ServiceAccountPrivateKeyData)
  implicit val ServiceAccountKeyFormat = jsonFormat4(ServiceAccountKey)
}