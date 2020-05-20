package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Blocker, ContextShift, Sync, Timer}
import com.google.cloud.bigquery.{BigQuery, JobId, QueryJobConfiguration, TableResult}
import io.chrisdavenport.log4cats.StructuredLogger


private[google2] class GoogleBigQueryInterpreter[F[_]: Sync: ContextShift: Timer: StructuredLogger](client: BigQuery,
                                                                                                    blocker: Blocker)
  extends GoogleBigQueryService[F] {

  override def query(queryJobConfiguration: QueryJobConfiguration, options: BigQuery.JobOption*): F[TableResult] = {
    withLogging(blockingF(Sync[F].delay[TableResult] {
      client.query(queryJobConfiguration, options: _*)
    }), None, s"com.google.cloud.bigquery.BigQuery.query(${queryJobConfiguration.getQuery})")
  }

  override def query(queryJobConfiguration: QueryJobConfiguration, jobId: JobId, options: BigQuery.JobOption*): F[TableResult] = {
    withLogging(blockingF(Sync[F].delay[TableResult] {
      client.query(queryJobConfiguration, jobId, options: _*)
    }), None, s"com.google.cloud.bigquery.BigQuery.query(${queryJobConfiguration.getQuery})")
  }

  private def blockingF[A](fa: F[A]): F[A] = blocker.blockOn(fa)
}
