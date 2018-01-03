package org.broadinstitute.dsde.workbench.google.mock

import com.google.api.services.bigquery.model.{GetQueryResultsResponse, Job, JobReference}
import org.broadinstitute.dsde.workbench.google.GoogleBigQueryDAO
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.concurrent.Future

class MockGoogleBigQueryDAO extends GoogleBigQueryDAO {

  val testToken = "token"
  val testProject = GoogleProject("firecloud-project")
  val testQuery = "SELECT * FROM users"

  val testJobReference: JobReference = new JobReference().setJobId("test job id")
  val testJob: Job = new Job().setJobReference(testJobReference)
  val testResponse = new GetQueryResultsResponse

  override def startQuery(accessToken: String, project: GoogleProject, querySql: String): Future[JobReference] = {
    if (accessToken == testToken && project == testProject && querySql == testQuery)
      Future.successful(testJobReference)
    else
      Future.failed(throw new Exception("MockGoogleBigQueryDAO had unexpected inputs"))
  }

  override def getQueryStatus(accessToken: String, jobRef: JobReference): Future[Job] =  {
    if (accessToken == testToken && jobRef == testJobReference)
      Future.successful(testJob)
    else
      Future.failed(throw new Exception("MockGoogleBigQueryDAO had unexpected inputs"))
  }

  override def getQueryResult(accessToken: String, job: Job): Future[GetQueryResultsResponse] =  {
    if (accessToken == testToken && job == testJob)
      Future.successful(testResponse)
    else
      Future.failed(throw new Exception("MockGoogleBigQueryDAO had unexpected inputs"))
  }
}
