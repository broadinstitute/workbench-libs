package org.broadinstitute.dsde.workbench.dao

import java.io.File

import akka.actor.ActorSystem
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.google.{GoogleCredentialModes, HttpGoogleBigQueryDAO, HttpGoogleIamDAO, HttpGoogleStorageDAO}
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Google {
  val appName = "automation"
  val metricBaseName = appName
  lazy val system = ActorSystem()
  val ec: ExecutionContextExecutor = ExecutionContext.global

  val pemMode = GoogleCredentialModes.Pem(WorkbenchEmail(Config.GCS.qaEmail), new File(Config.GCS.pathToQAPem))

  lazy val googleIamDAO = new HttpGoogleIamDAO(appName, pemMode, metricBaseName)(system, ec)
  lazy val googleBigQueryDAO = new HttpGoogleBigQueryDAO(appName, pemMode, metricBaseName)(system, ec)
  lazy val googleStorageDAO = new HttpGoogleStorageDAO(appName, pemMode, metricBaseName)(system, ec)
}
