package org.broadinstitute.dsde.workbench.errorReporting

import java.nio.file.Paths

import cats.effect.IO
import com.google.devtools.clouderrorreporting.v1beta1.{ProjectName, SourceLocation}

import scala.concurrent.ExecutionContext.global
import scala.util.control.NoStackTrace

object ErrorReportingManualTest {
  implicit val cs = IO.contextShift(global)

  private def test(reporting: ErrorReporting[IO]): IO[Unit] =
    for {
      _ <- reporting.reportError(new Exception("eeee2"))
      _ <- reporting.reportError(
        "error2",
        SourceLocation
          .newBuilder()
          .setFunctionName("qi-function")
          .setFilePath(this.getClass.getName)
//          .setLineNumber(10)
          .build()
      )
    } yield ()

  def run(): Unit = {
    val res = ErrorReporting
      .fromPath[IO](Paths.get("/Users/qi/.google/qi-billing-90828dd5e7b8.json"),
                    "qi-test-app",
                    ProjectName.of("qi-billing"))
      .use(c => test(c))

    res.unsafeRunSync()
  }
}

final case class CustomException(msg: String) extends NoStackTrace
