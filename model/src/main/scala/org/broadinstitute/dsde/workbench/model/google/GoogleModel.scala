package org.broadinstitute.dsde.workbench.model.google

import java.time.Instant
import java.time.format.DateTimeFormatter

import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.model.google.GcsEntityTypes.{GcsEntityType, Project}
import org.broadinstitute.dsde.workbench.model.google.ProjectTeamTypes.ProjectTeamType
import spray.json.{JsString, JsValue, RootJsonFormat}

// Projects
case class GoogleProject(value: String) extends ValueObject
case class ProjectNumber(value: String) extends ValueObject

// Service Accounts
case class ServiceAccount(subjectId: ServiceAccountSubjectId, email: WorkbenchEmail, displayName: ServiceAccountDisplayName)
case class ServiceAccountSubjectId(value: String) extends ValueObject //The SA's Subject ID.
case class ServiceAccountName(value: String) extends ValueObject //The left half of the SA's email.
case class ServiceAccountDisplayName(value: String) extends ValueObject //A friendly name.

case class ServiceAccountKeyId(value: String) extends ValueObject
case class ServiceAccountPrivateKeyData(value: String) extends ValueObject with Base64Support
case class ServiceAccountKey(id: ServiceAccountKeyId, privateKeyData: ServiceAccountPrivateKeyData, validAfter: Option[Instant], validBefore: Option[Instant])

// Test IAM types.
case class IamPermission(value: String) extends ValueObject
case class IamTestResponse(permissions: Option[List[IamPermission]])

// Storage
case class GcsBucketName(value: String) extends ValueObject
case class GcsObjectName(value: String, timeCreated: Instant = Instant.EPOCH)
case class GcsPath(bucketName: GcsBucketName, objectName: GcsObjectName)
case class GcsParseError(value: String) extends ValueObject

object GcsLifecycleTypes {
  sealed trait GcsLifecycleType extends ValueObject
  case object Delete extends GcsLifecycleType { val value = "Delete" }
  case object SetStorageClass extends GcsLifecycleType { val value = "SetStorageClass" }

  def withName(name: String): GcsLifecycleType = name.toLowerCase() match {
    case "delete" => Delete
    case "setstorageclass" => SetStorageClass
    case _ => throw new WorkbenchException(s"Invalid lifecycle type: $name")
  }
}

object GcsRoles {
  sealed trait GcsRole extends ValueObject
  case object Reader extends GcsRole { val value = "READER" }
  case object Writer extends GcsRole { val value = "WRITER" }
  case object Owner extends GcsRole { val value = "OWNER" }

  def withName(name: String): GcsRole = name.toLowerCase() match {
    case "reader" => Reader
    case "writer" => Writer
    case "owner" => Owner
    case _ => throw new WorkbenchException(s"Invalid role: $name")
  }
}

object ProjectTeamTypes {
  sealed trait ProjectTeamType extends ValueObject
  case object Viewers extends ProjectTeamType { val value = "viewers" }
  case object Editors extends ProjectTeamType { val value = "editors" }
  case object Owners extends ProjectTeamType { val value = "owners" }

  def withName(name: String): ProjectTeamType = name.toLowerCase() match {
    case "viewers" => Viewers
    case "editors" => Editors
    case "owners" => Owners
    case _ => throw new WorkbenchException(s"Invalid project team type: $name")
  }
}

object GcsEntityTypes {
  sealed trait GcsEntityType extends ValueObject
  case object User extends GcsEntityType { val value = "user" }
  case object Group extends GcsEntityType { val value = "group" }
  case object Project extends GcsEntityType { val value = "project" }

  def withName(name: String): GcsEntityType = name.toLowerCase() match {
    case "user" => User
    case "group" => Group
    case "project" => Project
  }
}

sealed trait GcsEntity {
  val entityType: GcsEntityType
}
object GcsEntity {
  @deprecated("Please use EmailGcsEntity or ProjectGcsEntity instead.", "workbench-libs/workbench-model 0.11")
  def apply(email: WorkbenchEmail, entityType: GcsEntityType): GcsEntity = EmailGcsEntity(entityType, email)
}
case class EmailGcsEntity(entityType: GcsEntityType, email: WorkbenchEmail) extends GcsEntity {
  override def toString: String = s"${entityType.value}-${email.value}"
}
case class ProjectGcsEntity(team: ProjectTeamType, projectNumber: ProjectNumber) extends GcsEntity {
  val entityType: GcsEntityType = Project
  override def toString: String = s"${entityType.value}-${team.value}-${projectNumber.value}"
}

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

  implicit val GcsBucketNameFormat = ValueObjectFormat(GcsBucketName)
  implicit val GcsObjectNameFormat = jsonFormat2(GcsObjectName)
  implicit val GcsPathFormat = jsonFormat2(GcsPath)
  implicit val GcsParseErrorFormat = ValueObjectFormat(GcsParseError)
  implicit val GcsLifecycleTypeFormat = ValueObjectFormat(GcsLifecycleTypes.withName)
  implicit val GcsRoleFormat = ValueObjectFormat(GcsRoles.withName)
  implicit val GcsEntityTypeFormat = ValueObjectFormat(GcsEntityTypes.withName)
}
