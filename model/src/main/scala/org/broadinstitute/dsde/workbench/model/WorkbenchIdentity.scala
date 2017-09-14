package org.broadinstitute.dsde.workbench.model

import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}

/**
  * Created by mbemis on 8/23/17.
  */

object WorkbenchIdentityJsonSupport {
  import DefaultJsonProtocol._

  implicit object WorkbenchEmailFormat extends RootJsonFormat[WorkbenchEmail] {
    def write(e: WorkbenchEmail): JsString = e match {
      case userEmail: WorkbenchUserEmail => JsString(userEmail.value)
      case groupEmail: WorkbenchGroupEmail => JsString(groupEmail.value)
      case _ => throw new WorkbenchException("unable to marshal WorkbenchEmail")
    }

    def read(value: JsValue) = ???
  }

  implicit val WorkbenchUserIdFormat = jsonFormat1(WorkbenchUserId)
  implicit val WorkbenchUserEmailFormat = jsonFormat1(WorkbenchUserEmail)
  implicit val WorkbenchUserFormat = jsonFormat2(WorkbenchUser)

  implicit val WorkbenchGroupNameFormat = jsonFormat1(WorkbenchGroupName)
  implicit val WorkbenchGroupEmailFormat = jsonFormat1(WorkbenchGroupEmail)
  implicit val WorkbenchGroupFormat = jsonFormat3(WorkbenchGroup)

}

sealed trait WorkbenchSubject extends ValueObject
sealed trait WorkbenchEmail extends ValueObject

case class WorkbenchUser(id: WorkbenchUserId, email: WorkbenchUserEmail)
case class WorkbenchUserId(value: String) extends WorkbenchSubject
case class WorkbenchUserEmail(value: String) extends WorkbenchEmail

case class WorkbenchGroup(id: WorkbenchGroupName, members: Set[WorkbenchEmail], email: WorkbenchGroupEmail)
case class WorkbenchGroupName(value: String) extends WorkbenchSubject
case class WorkbenchGroupEmail(value: String) extends WorkbenchEmail
