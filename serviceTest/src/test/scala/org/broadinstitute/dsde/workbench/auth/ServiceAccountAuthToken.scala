package org.broadinstitute.dsde.workbench.auth

import java.io.ByteArrayInputStream
import java.util

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import org.broadinstitute.dsde.workbench.dao.Google.googleIamDAO
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.{GoogleProject, ServiceAccountKey}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

import scala.collection.JavaConverters._

case class ServiceAccountAuthTokenFromJson(privateKeyJsonString: String, scopes: Option[Seq[String]] = None) extends AuthToken {
  override def buildCredential(): GoogleCredential = {
    GoogleCredential.fromStream(new ByteArrayInputStream(privateKeyJsonString.getBytes())).createScoped(scopes.getOrElse(serviceAccountScopes).asJava)
  }
}

object ServiceAccountAuthTokenFromPem {
  def apply(clientId: String, pemFilePath: String, scopes: Option[Seq[String]] = None) = new ServiceAccountAuthTokenFromPem(clientId, pemFilePath, scopes)
}

class ServiceAccountAuthTokenFromPem(clientId: String, pemFilePath: String, scopes: Option[Seq[String]] = None) extends AuthToken {
  def buildCredential(): GoogleCredential = {
    val builder = new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(clientId)
      .setServiceAccountScopes(scopes.getOrElse(serviceAccountScopes).asJava)
      .setServiceAccountPrivateKeyFromPemFile(new java.io.File(pemFilePath))

    builder.build()
  }
}

case class TrialBillingAccountAuthToken() extends ServiceAccountAuthTokenFromPem(ServiceTestConfig.GCS.trialBillingPemFileClientId, ServiceTestConfig.GCS.trialBillingPemFile)