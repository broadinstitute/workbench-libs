package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import com.google.api.services.bigquery.{Bigquery, BigqueryScopes}
import com.google.api.services.bigquery.model._
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class HttpGoogleBigQueryDAO(appName: String,
                            googleCredentialMode: GoogleCredentialMode,
                            workbenchMetricBaseName: String)
                           (implicit system: ActorSystem, executionContext: ExecutionContext)
  extends AbstractHttpGoogleDAO(appName, googleCredentialMode, workbenchMetricBaseName) with GoogleBigQueryDAO {

  override val scopes = Seq(BigqueryScopes.BIGQUERY)

  override implicit val service = GoogleInstrumentedService.BigQuery

  private lazy val bigquery: Bigquery = {
    new Bigquery.Builder(httpTransport, jsonFactory, googleCredential).setApplicationName(appName).build()
  }

  private def submitQuery(projectId: String, job: Job): Future[JobReference] = {
    val queryRequest = bigquery.jobs.insert(projectId, job)

    retryWhen500orGoogleError { () =>
      executeGoogleRequest(queryRequest)
    } map { job =>
      job.getJobReference
    }
  }

  override def startQuery(project: GoogleProject, querySql: String): Future[JobReference] = {
    val job = new Job()
      .setConfiguration(new JobConfiguration()
        .setQuery(new JobConfigurationQuery()
          .setQuery(querySql)))

    submitQuery(project.value, job)
  }

  override def startParameterizedQuery(project: GoogleProject, querySql: String, queryParameters: List[QueryParameter], parameterMode: String): Future[JobReference] = {
    val job = new Job()
      .setConfiguration(new JobConfiguration()
        .setQuery(new JobConfigurationQuery()
          // This defaults to true in current version. Standard SQL is required for query parameters.
          .setUseLegacySql(false)
          .setParameterMode(parameterMode)
          .setQueryParameters(queryParameters.asJava)
          .setQuery(querySql)))

    submitQuery(project.value, job)
  }

  override def getQueryStatus(jobRef: JobReference): Future[Job] = {
    val statusRequest = bigquery.jobs.get(jobRef.getProjectId, jobRef.getJobId)

    retryWhen500orGoogleError { () =>
      executeGoogleRequest(statusRequest)
    }
  }

  override def getQueryResult(job: Job): Future[GetQueryResultsResponse] = {
    if (job.getStatus.getState != "DONE")
      Future.failed(new WorkbenchException(s"job ${job.getJobReference.getJobId} not done"))

    val resultRequest = bigquery.jobs.getQueryResults(job.getJobReference.getProjectId, job.getJobReference.getJobId)
    retryWhen500orGoogleError { () =>
      executeGoogleRequest(resultRequest)
    }
  }
}
