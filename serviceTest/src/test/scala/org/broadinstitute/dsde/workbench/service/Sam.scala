package org.broadinstitute.dsde.workbench.service

import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{UserPool, _}
import org.broadinstitute.dsde.workbench.dao.Google.googleIamDAO
import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchUserId}
import org.broadinstitute.dsde.workbench.model.google.{GoogleProject, ServiceAccountKey, ServiceAccountName}
import org.broadinstitute.dsde.workbench.service.Sam.user
import org.broadinstitute.dsde.workbench.service.Sam.user.UserStatusDetails
import org.scalatest.time.{Seconds, Span}
import org.scalatest.concurrent.ScalaFutures
import spray.json.{JsObject, JsValue}

/**
  * Sam API service client. This should only be used when Orchestration does
  * not provide a required endpoint. This should primarily be used for admin
  * functions.
  */
trait Sam extends RestClient with LazyLogging with ScalaFutures{

  val url = Config.FireCloud.samApiUrl

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  def petName(userInfo: user.UserStatusDetails) = ServiceAccountName(s"pet-${userInfo.userSubjectId}")

  def removePet(project: String, userInfo: UserStatusDetails): Unit = {
    Sam.admin.deletePetServiceAccount(project, userInfo.userSubjectId)(UserPool.chooseAdmin.makeAuthToken())
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

    def deletePetServiceAccount(project: String, userSubjectId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting pet service account in project $project for user $userSubjectId")
      deleteRequest(url + s"api/admin/user/$userSubjectId/petServiceAccount/$project")
    }
  }

}
object Sam extends Sam {

  object user {

    case class UserStatusDetails(userSubjectId: String, userEmail: String)

    case class UserStatus(userInfo: UserStatusDetails, enabled: Map[String, Boolean])

    def status()(implicit token: AuthToken): Option[UserStatus] = {
      logger.info(s"Getting user registration status")
      parseResponseOption[UserStatus](getRequest(url + "register/user"))
    }

    def petServiceAccountEmail(project: String)(implicit token: AuthToken): WorkbenchEmail = {
      logger.info(s"Getting pet service account email")
      val petEmailStr = parseResponseAs[String](getRequest(url + s"api/google/user/petServiceAccount/$project"))
      WorkbenchEmail(petEmailStr)
    }

    def petServiceAccountKey(project: String)(implicit token: AuthToken): String = {
      logger.info(s"Getting pet service account email")
      parseResponseAs[String](getRequest(url + s"api/google/user/petServiceAccount/$project/key"))
    }

    def proxyGroup(userEmail: String)(implicit token: AuthToken): WorkbenchEmail = {
      logger.info(s"Getting proxy group email")
      val proxyGroupEmailStr = parseResponseAs[String](getRequest(url + s"api/google/user/proxyGroup/$userEmail"))
      WorkbenchEmail(proxyGroupEmailStr)
    }

    def deletePetServiceAccountKey(project: String, keyId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting pet service account key $keyId in project $project")
      deleteRequest(url + s"api/google/user/petServiceAccount/$project/key/$keyId")
    }
  }

}
