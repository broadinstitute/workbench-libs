package org.broadinstitute.dsde.workbench.model

import akka.http.scaladsl.model.headers.OAuth2BearerToken

case class UserInfo(accessToken: OAuth2BearerToken, userId: WorkbenchUserId, userEmail: WorkbenchEmail, tokenExpiresIn: Long)
