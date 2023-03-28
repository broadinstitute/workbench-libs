package org.broadinstitute.dsde.workbench.service

import akka.http.scaladsl.model.StatusCode

case class RestException(message: String = null, cause: Throwable = null, statusCode: StatusCode = null)
    extends Exception(message, cause)

object RestException {
  def apply(message: String, statusCode: StatusCode) = new RestException(message, null, statusCode)
}
