package org.broadinstitute.dsde.workbench.google.mock

import com.google.api.services.bigquery.model.{GetQueryResultsResponse, Job, JobReference, QueryParameter}
import org.broadinstitute.dsde.workbench.google.GoogleBigQueryDAO
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.concurrent.Future

class MockGoogleBigQueryDAO extends GoogleBigQueryDAO {

  val testProject = GoogleProject("firecloud-project")
  val testQuery = "SELECT * FROM users"
  val testParamQuery = "SELECT * FROM @table"
  val testParameters = List[QueryParameter](new QueryParameter().set("table", "users"))
  val testParameterMode = "NAMED"
  val testJobReference: JobReference = new JobReference().setJobId("test job id")
  val testJob: Job = new Job().setJobReference(testJobReference)
  val testResponse = new GetQueryResultsResponse
  val unexpectedInputsError = "MockGoogleBigQueryDAO had unexpected inputs"

  override def startQuery(project: GoogleProject, querySql: String): Future[JobReference] = {
    if (project == testProject && querySql == testQuery)
      Future.successful(testJobReference)
    else
      Future.failed(throw new Exception(unexpectedInputsError))
  }

  override def startParameterizedQuery(project: GoogleProject,
                                       querySql: String,
                                       queryParameters: List[QueryParameter],
                                       parameterMode: String): Future[JobReference] = {
    if (project == testProject && querySql == testParamQuery && queryParameters.equals(queryParameters) && parameterMode == testParameterMode)
      Future.successful(testJobReference)
    else
      Future.failed(throw new Exception(unexpectedInputsError))
  }

  override def getQueryStatus(jobRef: JobReference): Future[Job] =  {
    if (jobRef == testJobReference)
      Future.successful(testJob)
    else
      Future.failed(throw new Exception(unexpectedInputsError))
  }

  override def getQueryResult(job: Job): Future[GetQueryResultsResponse] =  {
    if (job == testJob)
      Future.successful(testResponse)
    else
      Future.failed(throw new Exception(unexpectedInputsError))
  }
}
