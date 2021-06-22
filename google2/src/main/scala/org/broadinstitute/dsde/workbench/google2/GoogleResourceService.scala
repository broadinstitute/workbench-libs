package org.broadinstitute.dsde.workbench.google2

import java.nio.file.Path

import cats.Parallel
import cats.effect.{Async, Resource, Sync}
import cats.mtl.Ask
import com.google.api.services.compute.ComputeScopes
import com.google.auth.oauth2.GoogleCredentials
import org.typelevel.log4cats.StructuredLogger
import com.google.cloud.resourcemanager.{Project, ResourceManagerOptions}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import cats.effect.Temporal
import cats.effect.std.Semaphore

trait GoogleResourceService[F[_]] {
  def getProject(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Project]]

  // Here, a return of None indicates that the project does not exist.
  def getLabels(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Map[String, String]]]

  def getProjectNumber(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Long]]
}

object GoogleResourceService {
  def resource[F[_]: StructuredLogger: Async: Parallel: Temporal: ContextShift](
    pathToCredential: Path,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleResourceService[F]] =
    for {
      credential <- credentialResource(pathToCredential.toString)
      scopedCredential = credential.createScoped(ComputeScopes.CLOUD_PLATFORM)
      interpreter <- fromCredential(scopedCredential, blocker, blockerBound)
    } yield interpreter

  def fromCredential[F[_]: StructuredLogger: Async: Parallel: Temporal: ContextShift](
    googleCredentials: GoogleCredentials,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleResourceService[F]] =
    for {
      resourceClient <- Resource.make(
        Sync[F].delay(ResourceManagerOptions.newBuilder().setCredentials(googleCredentials).build().getService())
      )(_ => Sync[F].unit)
    } yield new GoogleResourceInterpreter[F](resourceClient, blocker, blockerBound)
}
