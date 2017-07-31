package org.broadinstitute.dsde.workbench

import java.util.concurrent.TimeUnit

import com.codahale.metrics.SharedMetricRegistries
import com.readytalk.metrics.StatsDReporter
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.Duration

package object metrics extends LazyLogging {
  def startStatsDReporter(host: String, port: Int, apiKey: String, period: Duration, registryName: String = "default"): Unit = {
    logger.info(s"Starting statsd reporter writing to [$host:$port] with period [${period.toMillis} ms]")
    val reporter = StatsDReporter.forRegistry(SharedMetricRegistries.getOrCreate(registryName))
      .prefixedWith(apiKey)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build(host, port)
    reporter.start(period.toMillis, period.toMillis, TimeUnit.MILLISECONDS)
  }
}
