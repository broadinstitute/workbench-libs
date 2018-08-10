package org.broadinstitute.dsde.workbench.model

import akka.http.scaladsl.model.headers.OAuth2BearerToken

case class UserInfo(accessToken: OAuth2BearerToken, user: WorkbenchUser, tokenExpiresIn: Long)
