package org.broadinstitute.dsde.workbench.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.broadinstitute.dsde.workbench.config.{Credentials, ServiceTestConfig}

import scala.jdk.CollectionConverters._

case class UserAuthToken(user: Credentials, scopes: Seq[String]) extends AuthToken {
  override def buildCredential(): GoogleCredential = {
    val pemfile = new java.io.File(ServiceTestConfig.GCS.pathToQAPem)
    val email = ServiceTestConfig.GCS.qaEmail

    new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(email)
      .setServiceAccountPrivateKeyFromPemFile(pemfile)
      .setServiceAccountScopes(scopes.asJava)
      .setServiceAccountUser(user.email)
      .build()
  }
}
