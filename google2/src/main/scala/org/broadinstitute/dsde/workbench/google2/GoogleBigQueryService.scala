package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Async, Resource}
import com.google.auth.Credentials
import com.google.cloud.ServiceOptions.getDefaultProjectId
import com.google.cloud.bigquery.BigQueryOptions.DefaultBigQueryFactory
import com.google.cloud.bigquery._
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.{BigQueryDatasetName, BigQueryTableName, GoogleProject}
import org.typelevel.log4cats.StructuredLogger

trait GoogleBigQueryService[F[_]] {
  def query(queryJobConfiguration: QueryJobConfiguration, options: BigQuery.JobOption*): F[TableResult]

  def query(queryJobConfiguration: QueryJobConfiguration, jobId: JobId, options: BigQuery.JobOption*): F[TableResult]

  def createDataset(datasetName: String,
                    labels: Map[String, String],
                    aclBindings: Map[Acl.Role, Seq[(WorkbenchEmail, Acl.Entity.Type)]]
  ): F[DatasetId]

  def deleteDataset(datasetName: String): F[Boolean]

  @deprecated(message = "Use getTable(BigQueryDatasetName, BigQueryTableName) instead", since = "0.21")
  def getTable(datasetName: String, tableName: String): F[scala.Option[Table]]

  def getTable(datasetName: BigQueryDatasetName, tableName: BigQueryTableName): F[scala.Option[Table]]

  def getTable(googleProjectName: GoogleProject,
               datasetName: BigQueryDatasetName,
               tableName: BigQueryTableName
  ): F[scala.Option[Table]]

  @deprecated(message = "Use getDataset(BigQueryDatasetName) instead", since = "0.21")
  def getDataset(datasetName: String): F[scala.Option[Dataset]]

  def getDataset(datasetName: BigQueryDatasetName): F[scala.Option[Dataset]]

  def getDataset(googleProjectName: GoogleProject, datasetName: BigQueryDatasetName): F[scala.Option[Dataset]]
}

object GoogleBigQueryService {
  def resource[F[_]: Async: StructuredLogger](
    pathToJson: String
  ): Resource[F, GoogleBigQueryService[F]] =
    credentialResource(pathToJson) flatMap (resource(_))

  def resource[F[_]: Async: StructuredLogger](
    pathToJson: String,
    projectId: GoogleProject
  ): Resource[F, GoogleBigQueryService[F]] =
    credentialResource(pathToJson) flatMap (resource(_, projectId))

  def resource[F[_]: Async: StructuredLogger](
    credentials: Credentials
  ): Resource[F, GoogleBigQueryService[F]] =
    resource(credentials, GoogleProject(getDefaultProjectId))

  def resource[F[_]: Async: StructuredLogger](
    credentials: Credentials,
    projectId: GoogleProject
  ): Resource[F, GoogleBigQueryService[F]] =
    for {
      client <- Resource.eval[F, BigQuery](
        Async[F].delay(
          new DefaultBigQueryFactory().create(
            BigQueryOptions
              .newBuilder()
              .setCredentials(credentials)
              .setProjectId(projectId.value)
              .build()
          )
        )
      )
    } yield new GoogleBigQueryInterpreter[F](client)
}
