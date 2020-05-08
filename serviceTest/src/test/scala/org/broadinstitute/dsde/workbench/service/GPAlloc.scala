package org.broadinstitute.dsde.workbench.service

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import spray.json.DefaultJsonProtocol

case class GPAllocProject(projectName: String, cromwellAuthBucketUrl: String)

trait GPAlloc extends RestClient with LazyLogging with SprayJsonSupport with DefaultJsonProtocol {

  private def apiUrl(s: String) =
    ServiceTestConfig.FireCloud.gpAllocApiUrl + s

  object projects {

    def requestProject(implicit token: AuthToken): Option[GPAllocProject] = {
      logger.info(s"Requesting GPAlloced project...")
      val response = getRequest(apiUrl(s"googleproject"))
      response.status match {
        case StatusCodes.OK =>
          val proj = parseResponseAs[GPAllocProject](response)
          logger.info(s"GPAlloc returned new project ${proj.projectName}")
          Some(proj)
        case _ =>
          logger.warn(s"GPAlloc returned ${response.status} ${extractResponseString(response)}")
          None
      }
    }

    def releaseProject(projectName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Releasing project $projectName")
      deleteRequest(apiUrl(s"googleproject/$projectName"))
    }
  }
}

object GPAlloc extends GPAlloc
