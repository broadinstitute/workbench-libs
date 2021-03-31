package org.broadinstitute.dsde.workbench.google2

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.Ask
import com.google.cloud.resourcemanager.{Project, ResourceManager}
import org.typelevel.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import cats.syntax.all._
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._

import scala.collection.JavaConverters._

private[google2] class GoogleResourceInterpreter[F[_]: StructuredLogger: Parallel: Timer: ContextShift](
  resourceClient: ResourceManager,
  blocker: Blocker,
  blockerBound: Semaphore[F]
)(implicit F: Async[F])
    extends GoogleResourceService[F] {

  override def getProject(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Project]] =
    for {
      project <- tracedLogging(
        blockerBound.withPermit(
          blocker.blockOn(
            recoverF(F.delay(resourceClient.get(project.value)), whenStatusCode(404))
          )
        ),
        s"com.google.cloud.resourcemanager.ResourceManager.get(${project.value})",
        showProject
      )
    } yield project

  override def getLabels(project: GoogleProject)(implicit ev: Ask[F, TraceId]): F[Option[Map[String, String]]] =
    for {
      project <- getProject(project)
    } yield project.map(_.getLabels.asScala.toMap)

  override def getProjectNumber(project: GoogleProject)(implicit ev: Ask[F, TraceId]): F[Option[Long]] =
    for {
      project <- getProject(project)
    } yield project.map(_.getProjectNumber)
}
