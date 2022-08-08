package org.broadinstitute.dsde.workbench.util2

final case class LoggableCloudCall(response: Option[String], result: String)
// Cloud VM name
final case class InstanceName(value: String) extends AnyVal

sealed trait RemoveObjectResult extends Product with Serializable
object RemoveObjectResult {
  def apply(res: Boolean): RemoveObjectResult = if (res) Removed else NotFound

  final case object Removed extends RemoveObjectResult
  final case object NotFound extends RemoveObjectResult
}
