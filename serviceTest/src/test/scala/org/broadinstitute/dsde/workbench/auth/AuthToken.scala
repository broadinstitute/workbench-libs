package org.broadinstitute.dsde.workbench.auth

import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.typesafe.scalalogging.LazyLogging

trait AuthToken extends LazyLogging {
  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val jsonFactory = JacksonFactory.getDefaultInstance
  val authScopes = Seq("profile", "email", "openid", "https://www.googleapis.com/auth/devstorage.full_control", "https://www.googleapis.com/auth/cloud-platform")
  val billingScope = Seq("https://www.googleapis.com/auth/cloud-billing")

  lazy val value: String = makeToken()

  def buildCredential(): GoogleCredential

  private def makeToken(): String = {
    val cred = buildCredential()
    try {
      cred.refreshToken()
      throw new TokenResponseException()
    } catch {
      case e: TokenResponseException =>
        logger.error("Encountered 4xx error getting access token. Details: \n" +
          s"Service Account: ${cred.getServiceAccountId} \n" +
          s"User: ${cred.getServiceAccountUser} \n" +
          s"Scopes: ${cred.getServiceAccountScopesAsString} \n" +
          s"Access Token: ${cred.getAccessToken} \n" +
          s"SA Private Key ID: ${cred.getServiceAccountPrivateKeyId}")
        throw e
    }
    cred.getAccessToken
  }
}
