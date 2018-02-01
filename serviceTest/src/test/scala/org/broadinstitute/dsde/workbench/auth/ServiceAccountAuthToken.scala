package org.broadinstitute.dsde.workbench.auth

import java.io.ByteArrayInputStream
import java.util

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.dao.Google.googleIamDAO
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.{GoogleProject, ServiceAccountKey}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

import scala.collection.JavaConverters._

case class ServiceAccountAuthToken(privateKeyJsonString: String) extends AuthToken with ScalaFutures {
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  override def buildCredential(): GoogleCredential = {
    GoogleCredential.fromStream(new ByteArrayInputStream(privateKeyJsonString.getBytes())).createScoped(authScopes.asJava)
  }
}

case class TrialBillingAccountAuthToken() extends AuthToken {
  val trialBillingPemFile = Config.GCS.trialBillingPemFile
  val trialBillingPemFileClientId = Config.GCS.trialBillingPemFileClientId

  def buildCredential(): GoogleCredential = {
    val scopes: util.Collection[String] = (authScopes ++ billingScope).asJavaCollection
    val builder = new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(trialBillingPemFileClientId)
      .setServiceAccountScopes(scopes)
      .setServiceAccountPrivateKeyFromPemFile(new java.io.File(trialBillingPemFile))

    builder.build()
  }
}
