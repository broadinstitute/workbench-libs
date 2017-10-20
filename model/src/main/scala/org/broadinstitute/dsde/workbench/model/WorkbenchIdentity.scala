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

  implicit val WorkbenchUserPetServiceAccountIdFormat = ValueObjectFormat(WorkbenchUserServiceAccountId)
  implicit val WorkbenchUserPetServiceAccountEmailFormat = ValueObjectFormat(WorkbenchUserServiceAccountEmail)
  implicit val workbenchUserPetServiceAccountDisplayNameFormat = ValueObjectFormat(WorkbenchUserServiceAccountDisplayName)
  implicit val WorkbenchUserPetServiceAccountFormat = jsonFormat3(WorkbenchUserServiceAccount)
}

sealed trait WorkbenchSubject extends ValueObject
sealed trait WorkbenchEmail extends ValueObject {
  def isServiceAccount: Boolean = value.endsWith(".gserviceaccount.com")
}
sealed trait WorkbenchPerson {
  val id: WorkbenchSubject
  val email: WorkbenchEmail
}

case class WorkbenchUser(id: WorkbenchUserId, email: WorkbenchUserEmail) extends WorkbenchPerson
case class WorkbenchUserId(value: String) extends WorkbenchSubject
case class WorkbenchUserEmail(value: String) extends WorkbenchEmail

case class WorkbenchGroup(name: WorkbenchGroupName, members: Set[WorkbenchSubject], email: WorkbenchGroupEmail)
case class WorkbenchGroupName(value: String) extends WorkbenchSubject
case class WorkbenchGroupEmail(value: String) extends WorkbenchEmail

case class WorkbenchUserServiceAccount(id: WorkbenchUserServiceAccountId, email: WorkbenchUserServiceAccountEmail, displayName: WorkbenchUserServiceAccountDisplayName) extends WorkbenchPerson
case class WorkbenchUserServiceAccountId(value: String) extends WorkbenchSubject
case class WorkbenchUserServiceAccountEmail(value: String) extends WorkbenchEmail
case class WorkbenchUserServiceAccountDisplayName(value: String) extends ValueObject