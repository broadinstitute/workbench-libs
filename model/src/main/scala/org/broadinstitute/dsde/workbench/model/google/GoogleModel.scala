package org.broadinstitute.dsde.workbench.model.google

import java.time.Instant
import java.time.format.DateTimeFormatter

import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.model.google.GcsPathParser.GCS_SCHEME
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
case class GcsRelativePath(value: String) extends ValueObject
case class GcsBucketName(value: String) extends ValueObject
case class GcsPath(bucketName: GcsBucketName, relativePath: GcsRelativePath)

sealed trait StoragePermission extends ValueObject
case object Reader extends StoragePermission { val value = "READER" }
case object Writer extends StoragePermission { val value = "WRITER" }
case object Owner extends StoragePermission { val value = "OWNER" }

case class StorageAcl(email: WorkbenchEmail, permission: StoragePermission)

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