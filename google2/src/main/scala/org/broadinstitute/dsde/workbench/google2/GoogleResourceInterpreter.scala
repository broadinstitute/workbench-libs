package org.broadinstitute.dsde.workbench.google2

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.mtl.Ask
import com.google.cloud.resourcemanager.{Project, ResourceManager}
import io.chrisdavenport.log4cats.StructuredLogger
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

  override def listProject(filter: Option[GoogleProject])(implicit ev: Ask[F, TraceId]): F[List[Project]] =
    for {
      projects <- tracedLogging(
        blockerBound.withPermit(
          blocker.blockOn(
            F.delay(
              filter
                .fold(resourceClient.list())(project =>
                  resourceClient.list(ResourceManager.ProjectListOption.filter(s"name:${project.value}"))
                )
                .getValues()
                .asScala
                .toList
            )
          )
        ),
        s"com.google.cloud.resourcemanager.ResourceManager.list(${filter.getOrElse("")})",
        showProjects
      )
    } yield projects
}
