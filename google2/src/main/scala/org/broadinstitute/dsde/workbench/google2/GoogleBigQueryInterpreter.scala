package org.broadinstitute.dsde.workbench.google2

import cats.Show
import cats.effect.{Blocker, ContextShift, Sync, Timer}
import com.google.cloud.bigquery.Acl.Group
import com.google.cloud.bigquery.{Acl, BigQuery, BigQueryOptions, Dataset, DatasetInfo, JobId, QueryJobConfiguration, TableResult}
import io.chrisdavenport.log4cats.StructuredLogger

import scala.jdk.CollectionConverters._

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

  override def createDataset(datasetName: String): F[Dataset] = {
    val datasetInfo = DatasetInfo.newBuilder(datasetName).build

    //TODO: add logging
    blockingF(Sync[F].delay[Dataset] {
      client.create(datasetInfo)
    })
  }

  override def setDatasetIam(datasetName: String, bindings: Map[Acl.Entity, Acl.Role]): F[Dataset] = {
    val dataset = client.getDataset(datasetName)
    val newAclList = bindings.map { case (email, role) =>
      Acl.of(email, role)
    }

    blockingF(Sync[F].delay[Dataset] {
      client.update(dataset.toBuilder.setAcl(newAclList.toList.asJava).build())
    })
  }

  private def blockingF[A](fa: F[A]): F[A] = blocker.blockOn(fa)
}
