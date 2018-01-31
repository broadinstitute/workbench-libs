package org.broadinstitute.dsde.workbench.google

import akka.actor.ActorSystem
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.metrics.GoogleInstrumentedService.GoogleInstrumentedService
import org.broadinstitute.dsde.workbench.util.FutureSupport

import scala.concurrent.ExecutionContext

/**
  * Abstract base class for all HTTP Google DAOs.
  */
abstract class AbstractHttpGoogleDAO protected (appName: String,
                                                credentialMode: GoogleCredentialMode,
                                                override val workbenchMetricBaseName: String)
                                               (implicit val system: ActorSystem, val executionContext: ExecutionContext)
  extends GoogleUtilities with FutureSupport {

  implicit val service: GoogleInstrumentedService

  def scopes: Seq[String]

  protected def googleCredential: GoogleCredential = credentialMode.toGoogleCredential(scopes)
}
