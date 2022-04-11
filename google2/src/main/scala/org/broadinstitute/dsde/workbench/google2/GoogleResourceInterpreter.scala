package org.broadinstitute.dsde.workbench.google2

import cats.effect.Async
import cats.effect.std.Semaphore
import cats.mtl.Ask
import cats.syntax.all._
import com.google.cloud.resourcemanager.{Project, ResourceManager}
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import scala.jdk.CollectionConverters._

private[google2] class GoogleResourceInterpreter[F[_]: StructuredLogger](
  resourceClient: ResourceManager,
  blockerBound: Semaphore[F]
)(implicit F: Async[F])
    extends GoogleResourceService[F] {

  override def getProject(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Project]] =
    for {
      project <- tracedLogging(
        blockerBound.permit.use(_ => recoverF(F.blocking(resourceClient.get(project.value)), whenStatusCode(404))),
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
