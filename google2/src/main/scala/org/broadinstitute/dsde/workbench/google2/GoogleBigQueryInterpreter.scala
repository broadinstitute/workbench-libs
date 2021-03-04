package org.broadinstitute.dsde.workbench.google2

import cats.Show
import cats.effect.{Blocker, ContextShift, Sync, Timer}
import com.google.cloud.bigquery.Acl.{Group, User}
import com.google.cloud.bigquery.{Acl, BigQuery, DatasetId, DatasetInfo, JobId, QueryJobConfiguration, TableResult}
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchException}

import scala.collection.JavaConverters._

private[google2] class GoogleBigQueryInterpreter[F[_]: Sync: ContextShift: Timer: StructuredLogger](client: BigQuery,
                                                                                                    blocker: Blocker
) extends GoogleBigQueryService[F] {

  private val tableResultFormatter: Show[TableResult] =
    Show.show((tableResult: TableResult) =>
      if (tableResult == null) "null" else s"total row count: ${tableResult.getTotalRows}"
    )

  override def query(queryJobConfiguration: QueryJobConfiguration, options: BigQuery.JobOption*): F[TableResult] =
    withLogging(
      blockingF(Sync[F].delay[TableResult] {
        client.query(queryJobConfiguration, options: _*)
      }),
      None,
      s"com.google.cloud.bigquery.BigQuery.query(${queryJobConfiguration.getQuery})",
      tableResultFormatter
    )

  override def query(queryJobConfiguration: QueryJobConfiguration,
                     jobId: JobId,
                     options: BigQuery.JobOption*
  ): F[TableResult] =
    withLogging(
      blockingF(Sync[F].delay[TableResult] {
        client.query(queryJobConfiguration, jobId, options: _*)
      }),
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
      blockingF(Sync[F].delay[DatasetId] {
        client.create(datasetInfo).getDatasetId
      }),
      None,
      s"com.google.cloud.bigquery.BigQuery.create(${datasetName})"
    )
  }

  override def deleteDataset(datasetName: String): F[Boolean] = {
    withLogging(
      blockingF(Sync[F].delay[Boolean] {
        client.delete(datasetName)
      }),
      None,
      s"com.google.cloud.bigquery.BigQuery.delete(${datasetName})"
    )
  }

  private def blockingF[A](fa: F[A]): F[A] = blocker.blockOn(fa)
}
