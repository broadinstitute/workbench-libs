package org.broadinstitute.dsde.workbench.dao

import java.io.File

import akka.actor.ActorSystem
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.google.{GoogleCredentialMode, HttpGoogleBigQueryDAO, HttpGoogleIamDAO}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Google {
  val appName = "automation"
  val metricBaseName = appName
  lazy val system = ActorSystem()
  val ec: ExecutionContextExecutor = ExecutionContext.global

  val pemMode = GoogleCredentialMode.Pem(Config.GCS.qaEmail, new File(Config.GCS.pathToQAPem))

  lazy val googleIamDAO = new HttpGoogleIamDAO(appName, pemMode, metricBaseName)(system, ec)
  lazy val googleBigQueryDAO = new HttpGoogleBigQueryDAO(appName, pemMode, metricBaseName)(system, ec)
}
