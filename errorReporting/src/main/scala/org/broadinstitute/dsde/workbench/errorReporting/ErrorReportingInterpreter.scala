package org.broadinstitute.dsde.workbench.errorReporting

import cats.effect.Sync
import cats.implicits._
import com.google.cloud.errorreporting.v1beta1.ReportErrorsServiceClient
import com.google.devtools.clouderrorreporting.v1beta1._
import java.io.PrintWriter
import java.io.StringWriter

class ErrorReportingInterpreter[F[_]](appName: String, projectName: ProjectName, client: ReportErrorsServiceClient)(
  implicit F: Sync[F]
) extends ErrorReporting[F] {
  val serviceContext = ServiceContext.newBuilder().setService(appName)

  override def reportError(msg: String, sourceLocation: SourceLocation): F[Unit] = {
    val errorEvent = ReportedErrorEvent
      .newBuilder()
      .setMessage(msg)
      .setServiceContext(serviceContext)
      .setContext(ErrorContext.newBuilder().setReportLocation(sourceLocation))
      .build()

    F.delay(client.reportErrorEvent(projectName, errorEvent))
  }

  override def reportError(t: Throwable): F[Unit] = {
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    for {
      _ <- F.delay(t.printStackTrace(pw))
      errorEvent = ReportedErrorEvent
        .newBuilder()
        .setMessage(sw.toString)
        .setServiceContext(serviceContext)
        .build()
      _ <- F.delay(client.reportErrorEvent(projectName, errorEvent))
    } yield ()
  }
}
