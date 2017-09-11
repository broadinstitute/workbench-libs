package org.broadinstitute.dsde.workbench.metrics

import nl.grons.metrics.scala.{Gauge => GronsGauge}
import nl.grons.metrics.scala.{Counter => GronsCounter}
import nl.grons.metrics.scala.{Timer => GronsTimer}
import nl.grons.metrics.scala.{Histogram => GronsHisto}

//Wrapper classes around scala-metrics Metrics so they don't immediately forget their own names.

sealed trait Metric[M] {
  val name: String
  val metric: M
  override def toString: String = name
}

class Gauge[T](val name: String, val metric: GronsGauge[T]) extends Metric[GronsGauge[T]]
class Counter(val name: String, val metric: GronsCounter) extends Metric[GronsCounter]
class Timer(val name: String, val metric: GronsTimer) extends Metric[GronsTimer]
class Histogram(val name: String, val metric: GronsHisto) extends Metric[GronsHisto]
