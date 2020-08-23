package org.broadinstitute.dsde.workbench.model

import java.util.UUID

// unique identifier for tracing a unique call flow in logging
final case class TraceId(asString: String) extends AnyVal

object TraceId {
  def apply(uuid: UUID): TraceId = new TraceId(uuid.toString)
}

final case class IP(asString: String) extends AnyVal
