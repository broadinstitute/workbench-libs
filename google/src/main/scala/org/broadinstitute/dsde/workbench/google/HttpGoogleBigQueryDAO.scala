package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.bigquery.model._
import com.google.api.services.bigquery.Bigquery
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.concurrent.{ExecutionContext, Future}

class HttpGoogleBigQueryDAO(appName: String,
                            override val workbenchMetricBaseName: String)
                           (implicit val system: ActorSystem, val executionContext: ExecutionContext) extends GoogleBigQueryDAO with GoogleUtilities {

  implicit val service = GoogleInstrumentedService.BigQuery

  lazy val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  lazy val jsonFactory = JacksonFactory.getDefaultInstance

  private def bigquery(accessToken: String): Bigquery = {
    val credential = new GoogleCredential().setAccessToken(accessToken)
    new Bigquery.Builder(httpTransport, jsonFactory, credential).setApplicationName(appName).build()
  }

  override def startQuery(accessToken: String, project: GoogleProject, querySql: String): Future[JobReference] = {
    val job = new Job()
      .setConfiguration(new JobConfiguration()
        .setQuery(new JobConfigurationQuery()
          .setQuery(querySql)))

    val queryRequest = bigquery(accessToken).jobs.insert(project.value, job)

    retryWhen500orGoogleError { () =>
      executeGoogleRequest(queryRequest)
    } map { job =>
      job.getJobReference
    }
  }

  override def getQueryStatus(accessToken: String, jobRef: JobReference): Future[Job] = {
    val statusRequest = bigquery(accessToken).jobs.get(jobRef.getProjectId, jobRef.getJobId)

    retryWhen500orGoogleError { () =>
      executeGoogleRequest(statusRequest)
    }
  }

  override def getQueryResult(accessToken: String, job: Job): Future[GetQueryResultsResponse] = {
    if (job.getStatus.getState != "DONE")
      Future.failed(new WorkbenchException(s"job ${job.getJobReference.getJobId} not done"))

    val resultRequest = bigquery(accessToken).jobs.getQueryResults(job.getJobReference.getProjectId, job.getJobReference.getJobId)
    retryWhen500orGoogleError { () =>
      executeGoogleRequest(resultRequest)
    }
  }
}
