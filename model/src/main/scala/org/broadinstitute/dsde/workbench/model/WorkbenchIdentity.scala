package org.broadinstitute.dsde.workbench.model

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

  implicit val WorkbenchUserIdFormat = ValueObjectFormat(WorkbenchUserId)
  implicit val WorkbenchUserEmailFormat = ValueObjectFormat(WorkbenchUserEmail)
  implicit val WorkbenchUserFormat = jsonFormat2(WorkbenchUser)

  implicit val WorkbenchGroupNameFormat = ValueObjectFormat(WorkbenchGroupName)
  implicit val WorkbenchGroupEmailFormat = ValueObjectFormat(WorkbenchGroupEmail)

  implicit val WorkbenchUserPetServiceAccountIdFormat = ValueObjectFormat(WorkbenchUserPetServiceAccountId)
  implicit val WorkbenchUserPetServiceAccountEmailFormat = ValueObjectFormat(WorkbenchUserPetServiceAccountEmail)
  implicit val workbenchUserPetServiceAccountDisplayNameFormat = ValueObjectFormat(WorkbenchUserPetServiceAccountDisplayName)
  implicit val WorkbenchUserPetServiceAccountFormat = jsonFormat3(WorkbenchUserPetServiceAccount)
}

sealed trait WorkbenchSubject extends ValueObject
sealed trait WorkbenchEmail extends ValueObject

case class WorkbenchUser(id: WorkbenchUserId, email: WorkbenchUserEmail)
case class WorkbenchUserId(value: String) extends WorkbenchSubject
case class WorkbenchUserEmail(value: String) extends WorkbenchEmail {
  def isServiceAccount: Boolean = value.endsWith("gserviceaccount.com")
}

case class WorkbenchGroup(name: WorkbenchGroupName, members: Set[WorkbenchSubject], email: WorkbenchGroupEmail)
case class WorkbenchGroupName(value: String) extends WorkbenchSubject
case class WorkbenchGroupEmail(value: String) extends WorkbenchEmail

case class WorkbenchUserPetServiceAccount(id: WorkbenchUserPetServiceAccountId, email: WorkbenchUserPetServiceAccountEmail, displayName: WorkbenchUserPetServiceAccountDisplayName)
case class WorkbenchUserPetServiceAccountId(value: String) extends WorkbenchSubject
case class WorkbenchUserPetServiceAccountEmail(value: String) extends WorkbenchEmail
case class WorkbenchUserPetServiceAccountDisplayName(value: String) extends ValueObject