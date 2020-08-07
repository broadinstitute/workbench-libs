package org.broadinstitute.dsde.workbench.errorReporting

/**
 * Defines a ReportWorthy type class which has `def isReportWorthy(a: A): Boolean` function
 */
trait ReportWorthy[A] {
  def isReportWorthy(a: A): Boolean
}

final case class ReportWorthyOps[A](a: A)(implicit ev: ReportWorthy[A]) {
  def isReportWorthy: Boolean = ev.isReportWorthy(a)
}

object ReportWorthySyntax {
  implicit def reportWorthySyntax[A: ReportWorthy](a: A): ReportWorthyOps[A] = ReportWorthyOps(a)
}
