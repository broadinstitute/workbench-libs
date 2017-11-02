package org.broadinstitute.dsde.workbench.model

import java.time.Instant
import java.time.format.DateTimeFormatter

import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}

/**
  * Created by mbemis on 8/23/17.
  */

object WorkbenchIdentityJsonSupport {
  import DefaultJsonProtocol._

  implicit object WorkbenchEmailFormat extends RootJsonFormat[WorkbenchEmail] {
    def write(e: WorkbenchEmail): JsString = e match {
      case WorkbenchUserEmail(email) => JsString(email)
      case WorkbenchGroupEmail(email) => JsString(email)
      case _ => throw new WorkbenchException("unable to marshal WorkbenchEmail")
    }

    def read(value: JsValue) = ???
  }

  implicit object InstantFormat extends RootJsonFormat[Instant] {
    def write(instant: Instant): JsString = {
      JsString(DateTimeFormatter.ISO_INSTANT.format(instant))
    }

    def read(value: JsValue): Instant = value match {
      case JsString(str) => Instant.from(DateTimeFormatter.ISO_INSTANT.parse(str))
      case _ => throw new WorkbenchException(s"unable to unmarshal Instant from $value")
    }
  }

  implicit val WorkbenchUserIdFormat = ValueObjectFormat(WorkbenchUserId)
  implicit val WorkbenchUserEmailFormat = ValueObjectFormat(WorkbenchUserEmail)
  implicit val WorkbenchUserFormat = jsonFormat2(WorkbenchUser)

  implicit val WorkbenchGroupNameFormat = ValueObjectFormat(WorkbenchGroupName)
  implicit val WorkbenchGroupEmailFormat = ValueObjectFormat(WorkbenchGroupEmail)

  implicit val WorkbenchUserPetServiceAccountUniqueIdFormat = ValueObjectFormat(WorkbenchUserServiceAccountSubjectId)
  implicit val WorkbenchUserPetServiceAccountNameFormat = ValueObjectFormat(WorkbenchUserServiceAccountName)
  implicit val WorkbenchUserPetServiceAccountEmailFormat = ValueObjectFormat(WorkbenchUserServiceAccountEmail)
  implicit val workbenchUserPetServiceAccountDisplayNameFormat = ValueObjectFormat(WorkbenchUserServiceAccountDisplayName)
  implicit val WorkbenchUserPetServiceAccountFormat = jsonFormat3(WorkbenchUserServiceAccount)

  implicit val WorkbenchUserServiceAccountKeyIdFormat = ValueObjectFormat(WorkbenchUserServiceAccountKeyId)
  implicit val WorkbenchUserServiceAccountPrivateKeyDataFormat = ValueObjectFormat(WorkbenchUserServiceAccountPrivateKeyData)
  implicit val WorkbenchUserServiceAccountKeyFormat = jsonFormat4(WorkbenchUserServiceAccountKey)
}

sealed trait WorkbenchSubject
sealed trait WorkbenchEmail extends ValueObject

case class WorkbenchUser(id: WorkbenchUserId, email: WorkbenchUserEmail)
case class WorkbenchUserId(value: String) extends WorkbenchSubject with ValueObject
case class WorkbenchUserEmail(value: String) extends WorkbenchEmail

trait WorkbenchGroup { val id: WorkbenchGroupIdentity; val members: Set[WorkbenchSubject]; val email: WorkbenchGroupEmail }
trait WorkbenchGroupIdentity extends WorkbenchSubject
case class WorkbenchGroupName(value: String) extends WorkbenchGroupIdentity with ValueObject
case class WorkbenchGroupEmail(value: String) extends WorkbenchEmail

case class WorkbenchUserServiceAccount(subjectId: WorkbenchUserServiceAccountSubjectId, email: WorkbenchUserServiceAccountEmail, displayName: WorkbenchUserServiceAccountDisplayName)
case class WorkbenchUserServiceAccountSubjectId(value: String) extends WorkbenchSubject with ValueObject //The SA's Subject ID.
case class WorkbenchUserServiceAccountName(value: String) extends ValueObject //The left half of the SA's email.
case class WorkbenchUserServiceAccountEmail(value: String) extends WorkbenchEmail { //The SA's complete email.
  def toAccountName: WorkbenchUserServiceAccountName = WorkbenchUserServiceAccountName(value.split("@")(0))
}
case class WorkbenchUserServiceAccountDisplayName(value: String) extends ValueObject //A friendly name.

case class WorkbenchUserServiceAccountKeyId(value: String) extends ValueObject
case class WorkbenchUserServiceAccountPrivateKeyData(value: String) extends ValueObject with Base64Support
case class WorkbenchUserServiceAccountKey(id: WorkbenchUserServiceAccountKeyId, privateKeyData: WorkbenchUserServiceAccountPrivateKeyData, validAfter: Option[Instant], validBefore: Option[Instant])
