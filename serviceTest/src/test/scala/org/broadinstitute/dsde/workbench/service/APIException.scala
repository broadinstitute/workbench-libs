package org.broadinstitute.dsde.workbench.service

case class APIException(message: String = null, cause: Throwable = null) extends Exception(message, cause)
