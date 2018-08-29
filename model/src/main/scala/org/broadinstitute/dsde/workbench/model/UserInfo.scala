package org.broadinstitute.dsde.workbench.model

import akka.http.scaladsl.model.headers.OAuth2BearerToken

case class UserInfo(accessToken: OAuth2BearerToken, userId: WorkbenchUserId, userEmail: WorkbenchEmail, tokenExpiresIn: Long)
final case class CreateWorkbenchUserAPI(id: WorkbenchUserId, googleSubjectId: GoogleSubjectId, email: WorkbenchEmail)
