package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import com.google.auth.Credentials
import com.google.cloud.ServiceOptions.getDefaultProjectId
import com.google.cloud.bigquery.BigQueryOptions.DefaultBigQueryFactory
import com.google.cloud.bigquery.{BigQuery, BigQueryOptions, JobId, QueryJobConfiguration, TableResult}
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

trait GoogleBigQueryService[F[_]] {
  def query(queryJobConfiguration: QueryJobConfiguration, options: BigQuery.JobOption*): F[TableResult]

  def query(queryJobConfiguration: QueryJobConfiguration, jobId: JobId, options: BigQuery.JobOption*): F[TableResult]
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
    resource(credentials, blocker, GoogleProject(getDefaultProjectId))

  def resource[F[_]: Sync: ContextShift: Timer: StructuredLogger](
    credentials: Credentials,
    blocker: Blocker,
    projectId: GoogleProject
  ): Resource[F, GoogleBigQueryService[F]] =
    for {
      client <- Resource.liftF[F, BigQuery](
        Sync[F].delay(
          new DefaultBigQueryFactory().create(
            BigQueryOptions
              .newBuilder()
              .setCredentials(credentials)
              .setProjectId(projectId.value)
              .build()
          )
        )
      )
    } yield new GoogleBigQueryInterpreter[F](client, blocker)
}
