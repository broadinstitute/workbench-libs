package org.broadinstitute.dsde.workbench

import java.util.concurrent.TimeUnit

import com.codahale.metrics.SharedMetricRegistries
import com.readytalk.metrics.StatsDReporter
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.Duration

package object metrics extends LazyLogging {
  def startStatsDReporter(host: String, port: Int, period: Duration, registryName: String = "default", apiKey: Option[String] = None): Unit = {
    logger.info(s"Starting statsd reporter writing to [$host:$port] with period [${period.toMillis} ms]")
    val reporter = StatsDReporter.forRegistry(SharedMetricRegistries.getOrCreate(registryName))
      .prefixedWith(apiKey.orNull)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build(host, port)
    reporter.start(period.toMillis, period.toMillis, TimeUnit.MILLISECONDS)
  }

  implicit def metricUnwrapper[M](metric: Metric[M]): M = metric.metric
}
