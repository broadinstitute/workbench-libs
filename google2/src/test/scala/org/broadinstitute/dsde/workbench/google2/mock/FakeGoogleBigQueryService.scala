package org.broadinstitute.dsde.workbench.google2.mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.cloud.bigquery.BigQuery.TableOption
import com.google.cloud.bigquery.{BigQuery, JobId, QueryJobConfiguration, Table, TableInfo, TableResult}
import org.broadinstitute.dsde.workbench.google2.GoogleBigQueryService
import org.broadinstitute.dsde.workbench.model.TraceId

class FakeGoogleBigQueryService extends GoogleBigQueryService[IO] {
  def query(queryJobConfiguration: QueryJobConfiguration, options: BigQuery.JobOption*): IO[TableResult] =
    IO.pure(new TableResult(null, 10, null))

  def query(queryJobConfiguration: QueryJobConfiguration, jobId: JobId, options: BigQuery.JobOption*): IO[TableResult] =
    IO.pure(new TableResult(null, 10, null))

  def createTable(tableInfo: TableInfo, options: TableOption*)(implicit ev: Ask[IO, TraceId]): IO[Table] =
    IO.pure(new Table(null, null))
}
