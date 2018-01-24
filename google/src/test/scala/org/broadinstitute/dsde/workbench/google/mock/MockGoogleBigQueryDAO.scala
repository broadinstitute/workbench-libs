package org.broadinstitute.dsde.workbench.google.mock

import com.google.api.services.bigquery.model.{GetQueryResultsResponse, Job, JobReference}
import org.broadinstitute.dsde.workbench.google.GoogleBigQueryDAO
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.concurrent.Future

class MockGoogleBigQueryDAO extends GoogleBigQueryDAO {

  val testProject = GoogleProject("firecloud-project")
  val testQuery = "SELECT * FROM users"

  val testJobReference: JobReference = new JobReference().setJobId("test job id")
  val testJob: Job = new Job().setJobReference(testJobReference)
  val testResponse = new GetQueryResultsResponse

  override def startQuery(project: GoogleProject, querySql: String): Future[JobReference] = {
    if (project == testProject && querySql == testQuery)
      Future.successful(testJobReference)
    else
      Future.failed(throw new Exception("MockGoogleBigQueryDAO had unexpected inputs"))
  }

  override def getQueryStatus(jobRef: JobReference): Future[Job] =  {
    if (jobRef == testJobReference)
      Future.successful(testJob)
    else
      Future.failed(throw new Exception("MockGoogleBigQueryDAO had unexpected inputs"))
  }

  override def getQueryResult(job: Job): Future[GetQueryResultsResponse] =  {
    if (job == testJob)
      Future.successful(testResponse)
    else
      Future.failed(throw new Exception("MockGoogleBigQueryDAO had unexpected inputs"))
  }
}
