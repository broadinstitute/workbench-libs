package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import cats.mtl.Ask
import com.google.auth.Credentials
import com.google.cloud.bigquery.BigQuery.TableOption
import com.google.cloud.bigquery.BigQueryOptions.DefaultBigQueryFactory
import com.google.cloud.bigquery.{
  BigQuery,
  BigQueryOptions,
  JobId,
  QueryJobConfiguration,
  Table,
  TableInfo,
  TableResult
}
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.model.TraceId

trait GoogleBigQueryService[F[_]] {
  def query(queryJobConfiguration: QueryJobConfiguration, options: BigQuery.JobOption*): F[TableResult]

  def query(queryJobConfiguration: QueryJobConfiguration, jobId: JobId, options: BigQuery.JobOption*): F[TableResult]

  def createTable(tableInfo: TableInfo, options: TableOption*)(implicit ev: Ask[F, TraceId]): F[Table]
}

object GoogleBigQueryService {
  def resource[F[_]: Sync: ContextShift: Timer: StructuredLogger](
    pathToJson: String,
    blocker: Blocker
  ): Resource[F, GoogleBigQueryService[F]] =
    credentialResource(pathToJson) flatMap (resource(_, blocker))

  def resource[F[_]: Sync: ContextShift: Timer: StructuredLogger](
    credentials: Credentials,
    blocker: Blocker
  ): Resource[F, GoogleBigQueryService[F]] =
    for {
      client <- Resource.liftF[F, BigQuery](
        Sync[F].delay(
          new DefaultBigQueryFactory().create(
            BigQueryOptions
              .newBuilder()
              .setCredentials(credentials)
              .build()
          )
        )
      )
    } yield new GoogleBigQueryInterpreter[F](client, blocker)
}
