package org.broadinstitute.dsde.workbench.service

import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{UserPool, _}
import org.broadinstitute.dsde.workbench.dao.Google.googleDirectoryDAO
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

  val url = ServiceTestConfig.FireCloud.samApiUrl

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

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
      logger.info(s"Getting pet service account key")
      parseResponse(getRequest(url + s"api/google/user/petServiceAccount/$project/key"))
    }

    def arbitraryPetServiceAccountKey()(implicit token: AuthToken): String = {
      logger.info(s"Getting pet service account key")
      parseResponse(getRequest(url + s"api/google/user/petServiceAccount/key"))
    }

    def petServiceAccountToken(project: String, scopes: Set[String])(implicit token: AuthToken): String = {
      logger.info(s"Getting pet service account token")
      postRequest(url + s"api/google/user/petServiceAccount/$project/token", scopes)
    }

    def arbitraryPetServiceAccountToken(scopes: Set[String])(implicit token: AuthToken): String = {
      logger.info(s"Getting pet service account token")
      postRequest(url + s"api/google/user/petServiceAccount/token", scopes)
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

    def createGroup(group: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Creating managed group with id: $group")
      postRequest(url + s"api/groups/v1/$group")
    }

    def deleteGroup(group:String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting managed group with id: $group")
      deleteRequest(url + s"api/groups/v1/$group")
    }

    def syncPolicy(resourceType: String, resourceId: String, policy: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Syncing $policy in $resourceId of type $resourceType")
      postRequest(url + s"api/google/v1/resource/$resourceType/$resourceId/$policy/sync")
    }

    def addUser(group: String, policy: String, email: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding $email to $policy in $group")
      putRequest(url + s"api/groups/v1/$group/$policy/$email")
    }

    def deleteUser(group: String, policy: String, email: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing $email from $policy in $group")
      deleteRequest(url + s"api/groups/v1/$group/$policy/$email")
    }

    def getSyncState(resourceType: String, resourceId: String, policy: String)(implicit token: AuthToken): String = {
      logger.info(s"Getting sync state for $policy in $resourceId of type $resourceType")
      parseResponseAs[String](getRequest(url + s"api/google/v1/resource/$resourceType/$resourceId/$policy/sync"))
    }
//    def getPolicyMembers(group: String, policy: String)(implicit token: AuthToken): Set[WorkbenchEmail] = {
//      logger.info(s"Getting members of policy $policy in $group")
//      parseResponseAs[Set[String]](getRequest(url + s"api/groups/v1/$group/$policy")).map(WorkbenchEmail)
//    }

    def setPolicyMembers(group: String, policy: String, emails: Set[WorkbenchEmail])(implicit token: AuthToken): Unit = {
      logger.info(s"Overwriting members in policy $policy of $group")
      putRequest(url + s"api/groups/v1/$group/$policy", emails)
    }
  }

}
