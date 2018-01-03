package org.broadinstitute.dsde.workbench.google

import com.google.api.services.bigquery.model.{GetQueryResultsResponse, Job, JobReference}
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.concurrent.Future

trait GoogleBigQueryDAO {
  def startQuery(accessToken: String, project: GoogleProject, querySql: String): Future[JobReference]

  def getQueryStatus(accessToken: String, jobRef: JobReference): Future[Job]

  def getQueryResult(accessToken: String, job: Job): Future[GetQueryResultsResponse]
}