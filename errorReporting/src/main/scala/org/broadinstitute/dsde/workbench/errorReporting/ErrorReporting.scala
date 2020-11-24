package org.broadinstitute.dsde.workbench.errorReporting

import java.nio.file.Path

import cats.effect.{Resource, Sync}
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.google.cloud.errorreporting.v1beta1.{ReportErrorsServiceClient, ReportErrorsServiceSettings}
import com.google.devtools.clouderrorreporting.v1beta1.{ProjectName, SourceLocation}

import scala.collection.JavaConverters._

trait ErrorReporting[F[_]] {
  def reportError(msg: String, sourceLocation: SourceLocation): F[Unit]

  /**
   * @param t This throwable can not be NoStackTrace
   * @return
   */
  def reportError(t: Throwable): F[Unit]
}

object ErrorReporting {
  def fromPath[F[_]](pathToCredential: Path, appName: String, projectName: ProjectName)(implicit
    F: Sync[F]
  ): Resource[F, ErrorReporting[F]] =
    for {
      crendtialFile <- org.broadinstitute.dsde.workbench.util2.readPath(pathToCredential)
      credential = ServiceAccountCredentials
        .fromStream(crendtialFile)
        .createScoped(
          Set("https://www.googleapis.com/auth/cloud-platform").asJava
        )
      client <- fromCredential(credential, appName, projectName)
    } yield client

  def fromCredential[F[_]](credentials: GoogleCredentials, appName: String, projectName: ProjectName)(implicit
    F: Sync[F]
  ): Resource[F, ErrorReporting[F]] = {
    val settings = ReportErrorsServiceSettings
      .newBuilder()
      .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
      .build()
    Resource
      .make[F, ReportErrorsServiceClient](F.delay(ReportErrorsServiceClient.create(settings)))(c => F.delay(c.close()))
      .map(c => new ErrorReportingInterpreter[F](appName, projectName, c))
  }
}
