package org.broadinstitute.dsde.workbench.google2

import cats.Parallel
import cats.effect.std.Semaphore
import cats.effect.{Async, Resource, Sync}
import cats.mtl.Ask
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.resourcemanager.{Project, ResourceManagerOptions}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import java.nio.file.Path

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
  def resource[F[_]: StructuredLogger: Async: Parallel](
    pathToCredential: Path,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleResourceService[F]] =
    for {
      credential <- credentialResource(pathToCredential.toString)
      scopedCredential = credential.createScoped("https://www.googleapis.com/auth/cloud-platform")
      interpreter <- fromCredential(scopedCredential, blockerBound)
    } yield interpreter

  def fromCredential[F[_]: StructuredLogger: Async: Parallel](
    googleCredentials: GoogleCredentials,
    blockerBound: Semaphore[F]
  ): Resource[F, GoogleResourceService[F]] =
    for {
      resourceClient <- Resource.make(
        Sync[F].delay(ResourceManagerOptions.newBuilder().setCredentials(googleCredentials).build().getService())
      )(_ => Sync[F].unit)
    } yield new GoogleResourceInterpreter[F](resourceClient, blockerBound)
}
