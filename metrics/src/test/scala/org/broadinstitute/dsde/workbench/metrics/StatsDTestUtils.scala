package org.broadinstitute.dsde.workbench.metrics

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{MetricFilter, SharedMetricRegistries}
import com.readytalk.metrics.{StatsD, StatsDReporter}
import org.broadinstitute.dsde.workbench.util.MockitoTestUtils
import org.mockito.Mockito.{atLeastOnce, inOrder => mockitoInOrder}
import org.scalatest.concurrent.Eventually

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
  * Created by rtitle on 6/29/17.
  */
trait StatsDTestUtils { this: Eventually with MockitoTestUtils =>

  protected val workbenchMetricBaseName = "test"
  def clearRegistries(): Unit = SharedMetricRegistries.getOrCreate("default").removeMatching(MetricFilter.ALL)

  protected def withStatsD[T](testCode: => T)(verify: Seq[(String, String)] => Unit = _ => ()): T = {
    val statsD = mock[StatsD]
    clearRegistries()
    val reporter = StatsDReporter.forRegistry(SharedMetricRegistries.getOrCreate("default"))
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build(statsD)
    reporter.start(1, TimeUnit.SECONDS)
    try {
      val result = testCode
      eventually(timeout(10 seconds)) {
        val order = mockitoInOrder(statsD)
        order.verify(statsD).connect()
        val metricCaptor = captor[String]
        val valueCaptor = captor[String]
        order.verify(statsD, atLeastOnce).send(metricCaptor.capture, valueCaptor.capture)
        order.verify(statsD).close()
        verify(metricCaptor.getAllValues.asScala.zip(valueCaptor.getAllValues.asScala))
      }
      result
    } finally {
      reporter.stop()
      clearRegistries()
    }
  }

  protected def expectedHttpRequestMetrics(method: String, path: String, statusCode: Int, expectedTimes: Int): Set[(String, String)] = {
    val prefix = s"test.httpRequestMethod.$method.httpRequestUri.$path.httpResponseStatusCode.$statusCode"
    val expectedTimesStr = expectedTimes.toString
    Set(
      (s"$prefix.request", expectedTimesStr),
      (s"$prefix.latency.samples", expectedTimesStr)
    )
  }
}
