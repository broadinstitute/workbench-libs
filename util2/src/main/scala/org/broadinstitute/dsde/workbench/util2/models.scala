package org.broadinstitute.dsde.workbench.util2

final case class LoggableCloudCall(response: Option[String], result: String)
// Cloud VM name
final case class InstanceName(value: String) extends AnyVal
