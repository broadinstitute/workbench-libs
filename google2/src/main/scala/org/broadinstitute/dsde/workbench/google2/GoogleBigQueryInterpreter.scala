package org.broadinstitute.dsde.workbench.google2

import cats.Show
import cats.effect.Async
import com.google.cloud.bigquery.Acl.{Group, User}
import com.google.cloud.bigquery._
import org.broadinstitute.dsde.workbench.model.google.{BigQueryDatasetName, BigQueryTableName, GoogleProject}
import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchException}
import org.typelevel.log4cats.StructuredLogger

import scala.collection.JavaConverters._

private[google2] class GoogleBigQueryInterpreter[F[_]: StructuredLogger](client: BigQuery)(implicit F: Async[F])
    extends GoogleBigQueryService[F] {

  private val tableResultFormatter: Show[TableResult] =
    Show.show((tableResult: TableResult) =>
      if (tableResult == null) "null" else s"total row count: ${tableResult.getTotalRows}"
    )

  override def query(queryJobConfiguration: QueryJobConfiguration, options: BigQuery.JobOption*): F[TableResult] =
    withLogging(
      F.blocking[TableResult] {
        client.query(queryJobConfiguration, options: _*)
      },
      None,
      s"com.google.cloud.bigquery.BigQuery.query(${queryJobConfiguration.getQuery})",
      tableResultFormatter
    )

  override def query(queryJobConfiguration: QueryJobConfiguration,
                     jobId: JobId,
                     options: BigQuery.JobOption*
  ): F[TableResult] =
    withLogging(
      F.blocking[TableResult] {
        client.query(queryJobConfiguration, jobId, options: _*)
      },
      None,
      s"com.google.cloud.bigquery.BigQuery.query(${queryJobConfiguration.getQuery})",
      tableResultFormatter
    )

  override def createDataset(datasetName: String,
                             labels: Map[String, String],
                             aclBindings: Map[Acl.Role, Seq[(WorkbenchEmail, Acl.Entity.Type)]]
  ): F[DatasetId] = {
    val newAclList = aclBindings.flatMap { case (role, members) =>
      members.map { case (email, emailType) =>
        emailType match {
          case Acl.Entity.Type.GROUP => Acl.of(new Group(email.value), role)
          case Acl.Entity.Type.USER  => Acl.of(new User(email.value), role)
          case _                     => throw new WorkbenchException("unexpected email type")
        }
      }
    }

    val datasetInfo =
      DatasetInfo.newBuilder(datasetName).setLabels(labels.asJava).setAcl(newAclList.toList.asJava).build

    withLogging(
      F.blocking[DatasetId] {
        client.create(datasetInfo).getDatasetId
      },
      None,
      s"com.google.cloud.bigquery.BigQuery.create(${datasetName})"
    )
  }

  override def deleteDataset(datasetName: String): F[Boolean] =
    withLogging(
      F.blocking[Boolean] {
        client.delete(datasetName)
      },
      None,
      s"com.google.cloud.bigquery.BigQuery.delete(${datasetName})"
    )

  @deprecated(message = "Use getTable(BigQueryDatasetName, BigQueryTableName) instead", since = "0.21")
  override def getTable(datasetName: String, tableName: String): F[Option[Table]] =
    getTable(BigQueryDatasetName(datasetName), BigQueryTableName(tableName))

  override def getTable(datasetName: BigQueryDatasetName, tableName: BigQueryTableName): F[Option[Table]] =
    withLogging(
      F.blocking[Option[Table]] {
        Option(client.getTable(TableId.of(datasetName.value, tableName.value)))
      },
      None,
      s"com.google.cloud.bigquery.BigQuery.getTable(${datasetName.value}, ${tableName.value})"
    )

  override def getTable(googleProjectName: GoogleProject,
                        datasetName: BigQueryDatasetName,
                        tableName: BigQueryTableName
  ): F[Option[Table]] =
    withLogging(
      F.blocking[Option[Table]] {
        Option(client.getTable(TableId.of(googleProjectName.value, datasetName.value, tableName.value)))
      },
      None,
      s"com.google.cloud.bigquery.BigQuery.getTable(${googleProjectName.value}, ${datasetName.value}, ${tableName.value})"
    )

  @deprecated(message = "Use getDataset(BigQueryDatasetName) instead", since = "0.21")
  override def getDataset(datasetName: String): F[Option[Dataset]] = getDataset(BigQueryDatasetName(datasetName))

  override def getDataset(datasetName: BigQueryDatasetName): F[Option[Dataset]] =
    withLogging(
      F.blocking[Option[Dataset]] {
        Option(client.getDataset(DatasetId.of(datasetName.value)))
      },
      None,
      s"com.google.cloud.bigquery.BigQuery.getDataset(${datasetName.value})"
    )

  override def getDataset(googleProjectName: GoogleProject, datasetName: BigQueryDatasetName): F[Option[Dataset]] =
    withLogging(
      F.blocking[Option[Dataset]] {
        Option(client.getDataset(DatasetId.of(googleProjectName.value, datasetName.value)))
      },
      None,
      s"com.google.cloud.bigquery.BigQuery.getDataset(${googleProjectName.value}, ${datasetName.value})"
    )
}
