package org.broadinstitute.dsde.workbench.google2

import java.nio.file.Path

import cats.Parallel
import cats.effect.{Async, Blocker, ContextShift, Resource, Sync, Timer}
import cats.effect.concurrent.Semaphore
import cats.mtl.Ask
import com.google.api.services.compute.ComputeScopes
import com.google.auth.oauth2.GoogleCredentials
import io.chrisdavenport.log4cats.StructuredLogger
import com.google.cloud.resourcemanager.{Project, ResourceManagerOptions}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

trait GoogleResourceService[F[_]] {
  def getProject(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Project]]

  def listProject(filter: Option[GoogleProject])(implicit
    ev: Ask[F, TraceId]
  ): F[List[Project]]

  // Here, a return of None indicates that the project does not exist.
  def getLabels(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Map[String, String]]]

  def getProjectNumber(project: GoogleProject)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[Long]]
}

object GoogleResourceService {
  def resource[F[_]: StructuredLogger: Async: Parallel: Timer: ContextShift](
    pathToCredential: Path,
    blocker: Blocker,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleResourceService[F]] =
    for {
      credential <- credentialResource(pathToCredential.toString)
      scopedCredential = credential.createScoped(ComputeScopes.CLOUD_PLATFORM)
      interpreter <- fromCredential(scopedCredential, blocker, blockerBound)
    } yield interpreter

  def fromCredential[F[_]: StructuredLogger: Async: Parallel: Timer: ContextShift](
    googleCredentials: GoogleCredentials,
    blocker: Blocker,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleResourceService[F]] =
    for {
      resourceClient <- Resource.make(
        Sync[F].delay(ResourceManagerOptions.newBuilder().setCredentials(googleCredentials).build().getService())
      )(_ => Sync[F].unit)
    } yield new GoogleResourceInterpreter[F](resourceClient, blocker, blockerBound)
}
