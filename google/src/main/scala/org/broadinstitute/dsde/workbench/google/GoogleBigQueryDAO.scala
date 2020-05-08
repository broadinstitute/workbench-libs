package org.broadinstitute.dsde.workbench.google

import com.google.api.services.bigquery.model.{GetQueryResultsResponse, Job, JobReference, QueryParameter}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.concurrent.Future

trait GoogleBigQueryDAO {
  def startQuery(project: GoogleProject, querySql: String): Future[JobReference]

  def startParameterizedQuery(project: GoogleProject,
                              querySql: String,
                              queryParameters: List[QueryParameter],
                              parameterMode: String): Future[JobReference]

  def getQueryStatus(jobRef: JobReference): Future[Job]

  def getQueryResult(job: Job): Future[GetQueryResultsResponse]
}
