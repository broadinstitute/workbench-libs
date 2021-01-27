package org.broadinstitute.dsde.workbench.google2.mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.cloud.resourcemanager.Project
import org.broadinstitute.dsde.workbench.google2.GoogleResourceService
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class FakeGoogleResourceService extends GoogleResourceService[IO] {
  override def getProject(project: GoogleProject)(implicit ev: Ask[IO, TraceId]): IO[Option[Project]] = IO(None)

  override def listProject(filter: Option[GoogleProject])(implicit ev: Ask[IO, TraceId]): IO[List[Project]] = IO(
    List.empty
  )

  override def getLabels(project: GoogleProject)(implicit ev: Ask[IO, TraceId]): IO[Option[Map[String, String]]] = IO(
    None
  )

  override def getProjectNumber(project: GoogleProject)(implicit ev: Ask[IO, TraceId]): IO[Option[Long]] = IO(None)
}

object FakeGoogleResourceService extends FakeGoogleResourceService
