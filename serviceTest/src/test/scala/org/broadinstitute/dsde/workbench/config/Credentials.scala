package org.broadinstitute.dsde.workbench.config

import org.broadinstitute.dsde.workbench.auth.{AuthToken, UserAuthToken}

case class Credentials (email: String, password: String) {
  def makeAuthToken(): AuthToken = UserAuthToken(this)
}
