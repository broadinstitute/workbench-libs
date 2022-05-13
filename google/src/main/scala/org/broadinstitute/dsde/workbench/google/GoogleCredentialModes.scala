package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayInputStream, File}

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Charsets
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail

import scala.jdk.CollectionConverters._

/**
 * Created by rtitle on 1/30/18.
 */
object GoogleCredentialModes {
  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val jsonFactory = JacksonFactory.getDefaultInstance

  /**
   * Represents a way of obtaining a GoogleCredential.
   */
  sealed trait GoogleCredentialMode {
    def toGoogleCredential(scopes: Seq[String]): GoogleCredential
  }

  /**
   * Gets a GoogleCredential from a pem file.
   */
  case class Pem(serviceAccountClientId: WorkbenchEmail,
                 pemFile: File,
                 serviceAccountUser: Option[WorkbenchEmail] = None
  ) extends GoogleCredentialMode {
    def toGoogleCredential(scopes: Seq[String]): GoogleCredential =
      new GoogleCredential.Builder
        .setTransport(httpTransport)
        .setJsonFactory(jsonFactory)
        .setServiceAccountId(serviceAccountClientId.value)
        .setServiceAccountUser(serviceAccountUser.map(_.value).orNull)
        .setServiceAccountScopes(scopes.asJava)
        .setServiceAccountPrivateKeyFromPemFile(pemFile)
        .build
  }

  /**
   * Gets a GoogleCredential from a JSON key.
   */
  case class Json(json: String, serviceAccountUser: Option[WorkbenchEmail] = None) extends GoogleCredentialMode {
    def toGoogleCredential(scopes: Seq[String]) = {
      val creds = GoogleCredential.fromStream(new ByteArrayInputStream(json.getBytes(Charsets.UTF_8)))

      // unfortunately there is no built in way to set the service account user once creds are built from stream
      // so rebuild them (this used to call creds.createScoped which does the same thing under the hood)
      new GoogleCredential.Builder
        .setServiceAccountPrivateKey(creds.getServiceAccountPrivateKey)
        .setServiceAccountPrivateKeyId(creds.getServiceAccountPrivateKeyId)
        .setServiceAccountId(creds.getServiceAccountId)
        .setServiceAccountUser(serviceAccountUser.map(_.value).orNull)
        .setServiceAccountScopes(scopes.asJava)
        .setTokenServerEncodedUrl(creds.getTokenServerEncodedUrl)
        .setTransport(creds.getTransport)
        .setJsonFactory(creds.getJsonFactory)
        .setClock(creds.getClock)
        .build()
    }
  }

  /**
   * Gets a GoogleCredential from an access token. A function is passed in this case so
   * the token can be refreshed. For example:
   *
   * val dao = new HttpFooDAO("my-app", Token(() => obtainAccessToken()), ...)
   *
   * Note scopes are not used in this case since we are using pre-existing tokens.
   *
   * This implementation does not cache tokens or keep track of expiry. It invokes
   * the tokenProvider every time a token is needed. It is the caller's responsibility
   * to handle token expiration and refreshes.
   */
  case class Token(tokenProvider: () => String) extends GoogleCredentialMode {
    override def toGoogleCredential(scopes: Seq[String]): GoogleCredential =
      new GoogleCredential.setAccessToken(tokenProvider())
  }

  /**
   * GoogleCredential pass-through.
   */
  case class RawGoogleCredential(googleCredential: GoogleCredential) extends GoogleCredentialMode {
    override def toGoogleCredential(scopes: Seq[String]): GoogleCredential =
      googleCredential
  }
}
