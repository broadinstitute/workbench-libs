package org.broadinstitute.dsde.workbench.errorReporting

import cats.effect.{Resource, Sync}
import cats.syntax.all._
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
    val stackTraceWriter = Resource.make {
      val sw = new StringWriter
      F.delay(StackTraceWriter(sw, new PrintWriter(sw)))
    }(sw => F.delay(sw.printWriter.close()) >> F.delay(sw.stringWriter.close()))

    stackTraceWriter.use { w =>
      for {
        _ <- F.delay(t.printStackTrace(w.printWriter))
        errorEvent = ReportedErrorEvent
          .newBuilder()
          .setMessage(w.stringWriter.toString)
          .setServiceContext(serviceContext)
          .build()
        _ <- F.delay(client.reportErrorEvent(projectName, errorEvent))
      } yield ()
    }
  }
}

final private case class StackTraceWriter(stringWriter: StringWriter, printWriter: PrintWriter)
