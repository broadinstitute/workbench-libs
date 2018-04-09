package org.broadinstitute.dsde.workbench.service

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken

import scala.util.{Success, Try}

/**
  * Created by mbemis on 1/5/18.
  */
object Google extends FireCloudClient with LazyLogging {

  object billing {

    def removeBillingProjectAccount(projectName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing billing account from $projectName")
      putRequest(s"https://content-cloudbilling.googleapis.com/v1/projects/$projectName/billingInfo?fields=billingAccountName", Map("billingAccountName" -> ""))
    }

    def getBillingProjectAccount(projectName: String)(implicit token: AuthToken): Option[String] = {
      logger.info(s"Getting billing account associated with $projectName")
      parseResponseAs[Map[String, String]](getRequest(s"https://content-cloudbilling.googleapis.com/v1/projects/$projectName/billingInfo?fields=billingAccountName")).get("billingAccountName")
    }

    def canCreateBillingProjects(billingAccountId: String)(implicit token: AuthToken): Boolean = {
      logger.info(s"Checking to see if user can create projects in account $billingAccountId")
      //user has permissions if we get a 200 back
      Try {
        postRequest(s"https://cloudbilling.googleapis.com/v1/billingAccounts/01A82E-CA8A14-367457:testIamPermissions",
          Map("permissions" -> Seq("billing.resourceAssociations.create")))
      }.isSuccess
    }
  }
}
