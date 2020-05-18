package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Blocker, ContextShift, Sync}
import com.google.cloud.bigquery.{BigQuery, JobId, QueryJobConfiguration, TableResult}


private[google2] class GoogleBigQueryInterpreter[F[_]: Sync: ContextShift](client: BigQuery,
                                                                           blocker: Blocker) extends GoogleBigQueryService[F] {

  override def query(queryJobConfiguration: QueryJobConfiguration, options: BigQuery.JobOption*): F[TableResult] = {
    blockingF(Sync[F].delay[TableResult] {
      client.query(queryJobConfiguration, options: _*)
    })
  }

  override def query(queryJobConfiguration: QueryJobConfiguration, jobId: JobId, options: BigQuery.JobOption*): F[TableResult] = {
    blockingF(Sync[F].delay[TableResult] {
      client.query(queryJobConfiguration, jobId, options: _*)
    })
  }

  private def blockingF[A](fa: F[A]): F[A] = blocker.blockOn(fa)
}
