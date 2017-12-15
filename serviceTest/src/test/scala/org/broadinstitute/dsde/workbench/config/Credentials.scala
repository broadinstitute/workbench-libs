package org.broadinstitute.dsde.workbench.service.config

import org.broadinstitute.dsde.workbench.service.auth.{AuthToken, UserAuthToken}

case class Credentials (email: String, password: String) {
  def makeAuthToken(): AuthToken = UserAuthToken(this)
}
