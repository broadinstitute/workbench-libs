package org.broadinstitute.dsde.workbench.google2

import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import cats.effect.IO
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

final class GoogleDiskManualTest(pathToCredential: String) {
  def createDiskClone(destGoogleProject: GoogleProject,
                      zoneName: ZoneName,
                      destDiskName: DiskName,
                      srcGoogleProject: GoogleProject,
                      srcDiskName: DiskName
  ) = {
    implicit def logger = Slf4jLogger.getLogger[IO]
    implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))

    val blockerBound = Semaphore[IO](10).unsafeRunSync
    GoogleDiskService
      .resource(pathToCredential, blockerBound)
      .use { diskService =>
        for {
          srcDiskO <- diskService.getDisk(srcGoogleProject, zoneName, srcDiskName)
          srcDisk = srcDiskO.get
          r <- diskService.createDiskClone(destGoogleProject,
                                           zoneName,
                                           srcDisk.toBuilder.setName(destDiskName.value).build(),
                                           srcDisk
          )
        } yield r

      }
      .unsafeRunSync()
  }
}
