package org.broadinstitute.dsde.workbench.dao

import java.io.File

import akka.actor.ActorSystem
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes.Token
import org.broadinstitute.dsde.workbench.google.{GoogleCredentialModes, HttpGoogleBigQueryDAO, HttpGoogleIamDAO, HttpGoogleStorageDAO}
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Google {
  val appName = "automation"
  val metricBaseName: String = appName
  lazy val system = ActorSystem()
  val ec: ExecutionContextExecutor = ExecutionContext.global

  val pemMode = GoogleCredentialModes.Pem(WorkbenchEmail(Config.GCS.qaEmail), new File(Config.GCS.pathToQAPem))

  lazy val googleIamDAO = new HttpGoogleIamDAO(appName, pemMode, metricBaseName)(system, ec)
  def googleBigQueryDAO(authToken: AuthToken) {
    new HttpGoogleBigQueryDAO(appName, GoogleCredentialModes.RawGoogleCredential(authToken.buildCredential()), metricBaseName)(system, ec)
  }
  lazy val googleStorageDAO = new HttpGoogleStorageDAO(appName, pemMode, metricBaseName)(system, ec)
}
