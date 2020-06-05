package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import com.google.cloud.bigquery.BigQueryOptions.DefaultBigQueryFactory
import com.google.cloud.bigquery.{BigQuery, BigQueryOptions, JobId, QueryJobConfiguration, TableResult}
import io.chrisdavenport.log4cats.StructuredLogger


trait GoogleBigQueryService[F[_]] {
  def query(queryJobConfiguration: QueryJobConfiguration, options: BigQuery.JobOption*): F[TableResult]

  def query(queryJobConfiguration: QueryJobConfiguration, jobId: JobId, options: BigQuery.JobOption*): F[TableResult]
}

object GoogleBigQueryService {
  def resource[F[_]: Sync: ContextShift: Timer: StructuredLogger]
  (pathToJson: String, blocker: Blocker): Resource[F, GoogleBigQueryService[F]] =
    for {
      credentials <- credentialResource(pathToJson)
      client <- Resource.liftF[F, BigQuery](
        Sync[F].delay(
          new DefaultBigQueryFactory().create(
            BigQueryOptions.newBuilder()
              .setCredentials(credentials)
              .build())
        )
      )
    } yield new GoogleBigQueryInterpreter[F](client, blocker)
}


