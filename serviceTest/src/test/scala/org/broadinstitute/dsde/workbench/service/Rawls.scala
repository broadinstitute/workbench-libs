package org.broadinstitute.dsde.workbench.service

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.Config

trait Rawls extends RestClient with LazyLogging {

  val url = Config.FireCloud.rawlsApiUrl
  object admin {
    def deleteBillingProject(projectName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting billing project: $projectName")
      deleteRequest(url + s"api/admin/billing/$projectName")
    }

    def claimProject(projectName: String, cromwellAuthBucket: String, newOwner: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Claiming ownership of billing project: $projectName $newOwner")
      postRequest(url + s"api/admin/project/registration", Map("project" -> projectName, "bucket" -> cromwellAuthBucket, "newOwner" -> newOwner))
    }

    /**
      * Q: where is releaseProject?
      * A: There is no releaseProject. Rawls has no way of un-knowing about projects.
      *    Instead, we rely on the disappearance of the FiaB to erase all memory that this project existed.
      */

  }

  object workspaces {

    def create(namespace: String, name: String, authDomain: Set[String] = Set.empty)
              (implicit token: AuthToken): Unit = {
      logger.info(s"Creating workspace: $namespace/$name authDomain: $authDomain")

      val authDomainGroups = authDomain.map(a => Map("membersGroupName" -> a))

      val request = Map("namespace" -> namespace, "name" -> name,
        "attributes" -> Map.empty, "authorizationDomain" -> authDomainGroups)

      postRequest(url + s"api/workspaces", request)
    }

    def delete(namespace: String, name: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting workspace: $namespace/$name")
      deleteRequest(url + s"api/workspaces/$namespace/$name")
    }

    def list()(implicit token: AuthToken):String  = {
      logger.info(s"Listing workspaces")
      parseResponse(getRequest(url + s"api/workspaces"))
    }
  }
}

object Rawls extends Rawls
