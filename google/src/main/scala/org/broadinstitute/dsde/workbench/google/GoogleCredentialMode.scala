package org.broadinstitute.dsde.workbench.google

import java.io.{ByteArrayInputStream, File}

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Charsets

import scala.collection.JavaConverters._

/**
  * Created by rtitle on 1/30/18.
  */
object GoogleCredentialMode {
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
  case class Pem(serviceAccountClientId: String, serviceAccountUser: Option[String], pemFile: File) extends GoogleCredentialMode {
    def toGoogleCredential(scopes: Seq[String]) = {
      new GoogleCredential.Builder()
        .setTransport(httpTransport)
        .setJsonFactory(jsonFactory)
        .setServiceAccountId(serviceAccountClientId)
        .setServiceAccountUser(serviceAccountUser.orNull)
        .setServiceAccountScopes(scopes.asJava)
        .setServiceAccountPrivateKeyFromPemFile(pemFile)
        .build
    }
  }

  /**
    * Gets a GoogleCredential from a JSON key.
    */
  case class Json(json: String) extends GoogleCredentialMode {
    def toGoogleCredential(scopes: Seq[String]) = {
      GoogleCredential.fromStream(new ByteArrayInputStream(json.getBytes(Charsets.UTF_8))).createScoped(scopes.asJava)
    }
  }

  /**
    * Gets a GoogleCredential from an access token.
    * Note scopes are not used in this case since we are using a pre-existing token.
    */
  case class Token(token: String) extends GoogleCredentialMode {
    override def toGoogleCredential(scopes: Seq[String]): GoogleCredential = {
      new GoogleCredential().setAccessToken(token)
    }
  }
}
