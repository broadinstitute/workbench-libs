package org.broadinstitute.dsde.workbench.google2

object ManualTestImports {
  import org.broadinstitute.dsde.workbench.google2.GoogleStorageService
  import scala.concurrent.ExecutionContext.global
  import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
  import cats.effect.IO
  import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
  import org.broadinstitute.dsde.workbench.google2.GcsBlobName
  import org.broadinstitute.dsde.workbench.model.google.GoogleProject
  import org.broadinstitute.dsde.workbench.model.TraceId
  import cats.mtl.ApplicativeAsk
  import java.util.UUID
  import cats.effect.concurrent.Semaphore
  import org.broadinstitute.dsde.workbench.google2._
  import cats.effect.Blocker
  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit def logger = Slf4jLogger.getLogger[IO]
  implicit val traceId = ApplicativeAsk.const[IO, TraceId](TraceId(UUID.randomUUID()))
  val blocker = Blocker.liftExecutionContext(global)
  val blockerBound = Semaphore[IO](10).unsafeRunSync()
  val resource = GoogleDiskService.resource[IO]("/Users/gcarrill/Desktop/leo-dev-service-account.json/", blocker, blockerBound)
  resource.use(disk => disk.listDisks(GoogleProject("callisto-dev"), ZoneName("us-central1-a")).compile.toList)
}
