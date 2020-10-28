package org.broadinstitute.dsde.workbench.google2.util

import java.io.IOException

import com.google.api.gax.rpc.ApiException
import com.google.cloud.BaseServiceException
import org.broadinstitute.dsde.workbench.RetryConfig

import scala.concurrent.duration._

object RetryPredicates {
  val standardRetryConfig = RetryConfig(
    org.broadinstitute.dsde.workbench.util2.addJitter(1 seconds, 1 seconds),
    x => x * 2,
    5,
    standardRetryPredicate
  )

  def retryConfigWithPredicates(predicates: (Throwable => Boolean)*): RetryConfig =
    standardRetryConfig.copy(retryable = combine(predicates))

  /**
   * Retries anything google thinks is ok to retry plus any IOException
   * @return
   */
  def standardRetryPredicate: Throwable => Boolean = {
    case e: BaseServiceException => e.isRetryable
    case _: IOException          => true
    case _                       => false
  }

  def whenStatusCode(code: Int): Throwable => Boolean = {
    case e: BaseServiceException                                              => e.getCode == code
    case e: ApiException                                                      => e.getStatusCode.getCode.getHttpStatusCode == code
    case e: com.google.api.client.googleapis.json.GoogleJsonResponseException => e.getDetails.getCode == code
    case e: io.kubernetes.client.ApiException                                 => e.getCode == code
    case _                                                                    => false
  }

  def gkeRetryPredicate: Throwable => Boolean = {
    case e: io.grpc.StatusRuntimeException => e.getStatus.getCode == io.grpc.Status.Code.INTERNAL
  }

  def combine(predicates: Seq[Throwable => Boolean]): Throwable => Boolean = { throwable =>
    predicates.map(_(throwable)).foldLeft(false)(_ || _)
  }
}
