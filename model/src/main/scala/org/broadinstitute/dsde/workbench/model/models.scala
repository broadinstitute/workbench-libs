package org.broadinstitute.dsde.workbench.model

import java.util.UUID

// uuid for tracing a unique call flow in logging
final case class TraceId(uuid: UUID) extends AnyVal
