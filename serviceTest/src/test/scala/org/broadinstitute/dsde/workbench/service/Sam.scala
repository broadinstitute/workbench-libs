package org.broadinstitute.dsde.workbench.service

import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.service.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.config.{UserPool, _}
import org.broadinstitute.dsde.workbench.service.dao.Google
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.{GoogleProject, ServiceAccountName}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.concurrent.ScalaFutures

/**
  * Sam API service client. This should only be used when Orchestration does
  * not provide a required endpoint. This should primarily be used for admin
  * functions.
  */
class Sam(url: String) extends RestClient with LazyLogging with ScalaFutures{

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  def petName(userInfo: user.UserStatusDetails) = ServiceAccountName(s"pet-${userInfo.userSubjectId}")

  def removePet(userInfo: user.UserStatusDetails): Unit = {
    admin.deletePetServiceAccount(userInfo.userSubjectId)(UserPool.chooseAdmin.makeAuthToken())
    // TODO: why is this necessary?  GAWB-2867
    Google.googleIamDAO.removeServiceAccount(GoogleProject(Config.Projects.default), petName(userInfo)).futureValue
  }

  object admin {

    def deleteUser(subjectId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting user: $subjectId")
      deleteRequest(url + s"api/admin/user/$subjectId")
    }

    def doesUserExist(subjectId: String)(implicit token: AuthToken): Option[Boolean] = {
      getRequest(url + s"api/admin/user/$subjectId").status match {
        case StatusCodes.OK => Option(true)
        case StatusCodes.NotFound => Option(false)
        case _ => None
      }
    }

    def deletePetServiceAccount(userSubjectId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting pet service account for user $userSubjectId")
      deleteRequest(url + s"api/admin/user/$userSubjectId/petServiceAccount")
    }
  }

  object user {
    case class UserStatusDetails(userSubjectId: String, userEmail: String)
    case class UserStatus(userInfo: UserStatusDetails, enabled: Map[String, Boolean])

    def status()(implicit token: AuthToken): Option[UserStatus] = {
      logger.info(s"Getting user registration status")
      parseResponseOption[UserStatus](getRequest(url + "register/user"))
    }

    def petServiceAccountEmail()(implicit token: AuthToken): WorkbenchEmail = {
      logger.info(s"Getting pet service account email")
      val petEmailStr = parseResponseAs[String](getRequest(url + "api/user/petServiceAccount"))
      WorkbenchEmail(petEmailStr)
    }
  }
}