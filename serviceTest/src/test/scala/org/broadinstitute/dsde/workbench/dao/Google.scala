package org.broadinstitute.dsde.workbench.dao

import java.io.File

import akka.actor.ActorSystem
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import org.broadinstitute.dsde.workbench.google._
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes.RawGoogleCredential
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Google {
  val appName = "automation"
  val metricBaseName: String = appName
  lazy val system = ActorSystem()
  val ec: ExecutionContextExecutor = ExecutionContext.global

  val pemMode = GoogleCredentialModes.Pem(WorkbenchEmail("101291953224627454160"), new File(ServiceTestConfig.GCS.pathToQAPem), Option(WorkbenchEmail("google@test.firecloud.org")))

  lazy val googleIamDAO = new HttpGoogleIamDAO(appName, pemMode, metricBaseName)(system, ec)
  def googleBigQueryDAO(authToken: AuthToken): HttpGoogleBigQueryDAO = {
    new HttpGoogleBigQueryDAO(appName, RawGoogleCredential(authToken.buildCredential()), metricBaseName)(system, ec)
  }
  lazy val googleStorageDAO = new HttpGoogleStorageDAO(appName, pemMode, metricBaseName)(system, ec)
  lazy val googleDirectoryDAO = new HttpGoogleDirectoryDAO(appName, pemMode, metricBaseName)(system, ec)
}
