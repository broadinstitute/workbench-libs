package org.broadinstitute.dsde.workbench.auth

import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.service.util.Retry

import scala.concurrent.duration._

object AuthTokenScopes {
  // the list of scopes we request from end users when they log in.
  // this should always match exactly what the UI requests, so our tests represent actual user behavior:
  val userLoginScopes = Seq("profile", "email", "openid")
  // the list of scopes needed by service accounts to do their work:
  val serviceAccountScopes = userLoginScopes ++ Seq("https://www.googleapis.com/auth/devstorage.full_control", "https://www.googleapis.com/auth/cloud-platform")
  // list of scopes needed to work with billing.
  // TODO: change cloud-platform (very broad) to cloud-billing! This requires a change to the scopes that the service account is allowed to grant - right now, it can only grant cloud-platform, not cloud-billing.
  val billingScopes = userLoginScopes ++ Seq("https://www.googleapis.com/auth/cloud-platform")
}

trait AuthToken extends LazyLogging {
  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val jsonFactory = JacksonFactory.getDefaultInstance

  lazy val value: String = makeToken()

  def buildCredential(): GoogleCredential

  private def makeToken(): String = {
    Retry.retry(5.seconds, 1.minute)({
      val cred = buildCredential()
      try {
        cred.refreshToken()
        Option(cred.getAccessToken)
      } catch {
        case e: TokenResponseException if Set(StatusCodes.Unauthorized.intValue, StatusCodes.BadRequest.intValue) contains e.getStatusCode =>
          logger.error(s"Encountered ${e.getStatusCode} error getting access token. Details: \n" +
            s"Service Account: ${cred.getServiceAccountId} \n" +
            s"User: ${cred.getServiceAccountUser} \n" +
            s"Scopes: ${cred.getServiceAccountScopesAsString} \n" +
            s"Access Token: ${cred.getAccessToken} \n" +
            s"Token Expires: in ${cred.getExpiresInSeconds} seconds \n" +
            s"SA Private Key ID: ${cred.getServiceAccountPrivateKeyId}")
          None
      }
    })
  }.getOrElse(throw new Exception("Unable to get access token"))
}
