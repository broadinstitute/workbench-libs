package org.broadinstitute.dsde.workbench.metrics

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.codahale.metrics.{Gauge => DropwizardGauge}
import nl.grons.metrics4.scala.{DefaultInstrumented, MetricName}
import nl.grons.metrics4.scala.{Gauge => GronsGauge}
import org.broadinstitute.dsde.workbench.metrics.Expansion._
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

/**
 * Mixin trait for instrumentation.
 * Extends metrics-scala DefaultInstrumented and provides additional utilties for generating
 * metric names for Workbench.
 */
trait WorkbenchInstrumented extends DefaultInstrumented {

  /**
   * Base name for all metrics. This will be prepended to all generated metric names.
   * Example: dev.firecloud.rawls
   */
  protected val workbenchMetricBaseName: String
  override lazy val metricBaseName = MetricName(workbenchMetricBaseName)

  implicit def metricUnwrapper[M](metric: Metric[M]): M = metric.metric

  final val transientPrefix = "transient"

  /**
   * Utility for building expanded metric names in a typesafe way. Example usage:
   * {{{
   *   val counter: Counter =
   *     ExpandedMetricBuilder
   *       .expand(WorkspaceMetric, workspaceName)
   *       .expand(SubmissionMetric, submissionId)
   *       .expand(WorkflowStatusMetric, status)
   *       .asCounter("count")
   *   // counter has name:
   *   // <baseName>.workspace.<workspaceNamespace>.<workspaceName>.submission.<submissionId>.workflowStatus.<workflowStatus>.count
   *   counter += 1000
   * }}}
   *
   * Note the above will only compile if there are [[Expansion]] instances for the types passed to the expand method.
   */
  protected class ExpandedMetricBuilder private (m: String = "", _transient: Boolean = false) {
    def expand[A: Expansion](key: String, a: A): ExpandedMetricBuilder =
      new ExpandedMetricBuilder((if (m == "") m else m + ".") + implicitly[Expansion[A]].makeNameWithKey(key, a),
                                _transient
      )

    /**
     * Marks a metric as "transient". Transient metrics will automatically be deleted in Hosted
     * Graphite if they haven't received an update in X amount of time. It's usually good to set
     * metrics with high granularity (e.g. workspace or submission-level) as transient.
     */
    def transient(): ExpandedMetricBuilder =
      new ExpandedMetricBuilder(m, true)

    def getFullName(name: String): String =
      metricBaseName.append(makeName(name)).name

    def asCounter(name: String): Counter = {
      val cnt = metrics.counter(makeName(name))
      new Counter(getFullName(name), cnt)
    }

    def asGauge[T](name: String)(fn: => T): Gauge[T] = {
      val gauge = metrics.gauge(makeName(name))(fn)
      new Gauge[T](getFullName(name), gauge)
    }

    def asGaugeIfAbsent[T](name: String)(fn: => T): Gauge[T] = {
      // Get the fully qualified metric name for inspecting the registry.
      val gaugeName = getFullName(name)
      metricRegistry.getGauges().asScala.get(gaugeName) match {
        case None =>
          // If the gauge does not exist in the registry, create it
          asGauge[T](name)(fn)
        case Some(gauge) =>
          // If the gauge exists in the registry, return it.
          // Need to wrap the returned Java DropwizardGauge in a Scala Gauge.
          new Gauge[T](gaugeName, new GronsGauge[T](gauge.asInstanceOf[DropwizardGauge[T]]))
      }
    }

    def asTimer(name: String): Timer = {
      val tim = metrics.timer(makeName(name))
      new Timer(getFullName(name), tim)
    }

    def asHistogram(name: String): Histogram = {
      val histo = metrics.histogram(makeName(name))
      new Histogram(getFullName(name), histo)
    }

    def unregisterMetric[T](metric: Metric[T]): Boolean =
      metricRegistry.remove(metric.name)

    private def makeName(name: String): String = {
      val expandedName = if (m.nonEmpty) s"$m.$name" else name
      if (_transient) s"$transientPrefix.$expandedName" else expandedName
    }

    override def toString: String = m
  }

  object ExpandedMetricBuilder {
    def expand[A: Expansion](key: String, a: A): ExpandedMetricBuilder =
      new ExpandedMetricBuilder().expand(key, a)

    def empty: ExpandedMetricBuilder =
      new ExpandedMetricBuilder()
  }

  // Keys for expanded metric fragments
  final val HttpRequestMethodMetricKey = "httpRequestMethod"
  final val HttpRequestUriMetricKey = "httpRequestUri"
  final val HttpResponseStatusCodeMetricKey = "httpResponseStatusCode"

  // Handy definitions which can be used by implementing classes:

  protected def httpRequestMetricBuilder(
    builder: ExpandedMetricBuilder
  ): (HttpRequest, HttpResponse) => ExpandedMetricBuilder = { (httpRequest, httpResponse) =>
    builder
      .expand(HttpRequestMethodMetricKey, httpRequest.method)
      .expand(HttpRequestUriMetricKey, httpRequest.uri)
      .expand(HttpResponseStatusCodeMetricKey, httpResponse.status)
  }

  implicit protected def httpRequestCounter(implicit
    builder: ExpandedMetricBuilder
  ): (HttpRequest, HttpResponse) => Counter =
    httpRequestMetricBuilder(builder)(_, _).asCounter("request")

  implicit protected def httpRequestTimer(implicit
    builder: ExpandedMetricBuilder
  ): (HttpRequest, HttpResponse) => Timer =
    httpRequestMetricBuilder(builder)(_, _).asTimer("latency")

  implicit protected def httpRetryHistogram(implicit builder: ExpandedMetricBuilder): Histogram =
    builder.asHistogram("retry")
}
