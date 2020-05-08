package org.broadinstitute.dsde.workbench.service

case class RestException(message: String = null, cause: Throwable = null) extends Exception(message, cause)
