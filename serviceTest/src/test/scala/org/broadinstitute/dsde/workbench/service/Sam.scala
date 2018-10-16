package org.broadinstitute.dsde.workbench.service

import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{UserPool, _}
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.model.SamJsonSupport._
import org.broadinstitute.dsde.workbench.model.google.{GoogleProject, ServiceAccountKey, ServiceAccountName}
import org.broadinstitute.dsde.workbench.service.Sam.user
import org.broadinstitute.dsde.workbench.service.Sam.user.UserStatusDetails
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

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

    def createGroup(groupName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Creating managed group with id: $groupName")
      postRequest(url + s"api/groups/v1/$groupName")
    }

    def deleteGroup(groupName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting managed group with id: $groupName")
      deleteRequest(url + s"api/groups/v1/$groupName")
    }

    def syncPolicy(resourceTypeName: String, resourceId: String, policyName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Syncing $policyName in $resourceId of type $resourceTypeName")
      postRequest(url + s"api/google/v1/resource/$resourceTypeName/$resourceId/$policyName/sync")
    }

    def addUserToPolicy(groupName: String, policyName: String, memberEmail: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding $memberEmail to $policyName in $groupName")
      putRequest(url + s"api/groups/v1/$groupName/$policyName/$memberEmail")
    }

    def removeUserFromPolicy(groupName: String, policyName: String, memberEmail: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing $memberEmail from $policyName in $groupName")
      deleteRequest(url + s"api/groups/v1/$groupName/$policyName/$memberEmail")
    }

    def listResourcePolicies(resourceTypeName: String, resourceId: String)(implicit token: AuthToken): AccessPolicyResponseEntry = {
      logger.info(s"Listing policies for $resourceId")
      parseResponseAs[AccessPolicyResponseEntry](getRequest(url + s"api/resources/v1/$resourceTypeName/$resourceId/policies"))
    }

    def setPolicyMembers(groupName: String, policyName: String, memberEmails: Set[String])(implicit token: AuthToken): Unit = {
      logger.info(s"Overwriting members in policy $policyName of $groupName")
      putRequest(url + s"api/groups/v1/$groupName/$policyName", memberEmails)
    }
  }

}
