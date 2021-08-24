package org.broadinstitute.dsde.workbench.google2

import cats.effect.std.Semaphore
import cats.effect.{Async, Resource}
import cats.mtl.Ask
import cats.syntax.all._
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.compute.v1._
import fs2.Stream
import org.broadinstitute.dsde.workbench.DoneCheckableInstances.computeDoneCheckable
import org.broadinstitute.dsde.workbench.DoneCheckableSyntax._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.StructuredLogger

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

trait ComputePollOperation[F[_]] {
  implicit def F: Async[F]

  def getZoneOperation(project: GoogleProject, zoneName: ZoneName, operationName: OperationName)(implicit
    ev: Ask[F, TraceId]
  ): F[Operation]

  def getRegionOperation(project: GoogleProject, regionName: RegionName, operationName: OperationName)(implicit
    ev: Ask[F, TraceId]
  ): F[Operation]

  def getGlobalOperation(project: GoogleProject, operationName: OperationName)(implicit
    ev: Ask[F, TraceId]
  ): F[Operation]

  def pollOperation[A](project: GoogleProject,
                       operation: Operation,
                       delay: FiniteDuration,
                       maxAttempts: Int,
                       haltWhenTrue: Option[Stream[F, Boolean]]
  )(
    whenDone: F[A],
    whenTimeout: F[A],
    whenInterrupted: F[A]
  )(implicit
    ev: Ask[F, TraceId]
  ): F[A] =
    // TODO: once a newer version of the Java Compute SDK is released investigate using
    // the operation `wait` API instead of polling `get`. See:
    // https://cloud.google.com/compute/docs/reference/rest/v1/zoneOperations/wait
    // https://github.com/googleapis/java-compute/commit/50cb4a98cb36fcd3bf4bdd5d16ab17f9d391bf98
    (ZoneName.fromUriString(operation.getZone), RegionName.fromUriString(operation.getRegion)) match {
      case (Some(zone), _) =>
        pollZoneOperation(project, zone, OperationName(operation.getName), delay, maxAttempts, haltWhenTrue)(
          whenDone,
          whenTimeout,
          whenInterrupted
        )
      case (None, Some(region)) =>
        pollRegionOperation(project, region, OperationName(operation.getName), delay, maxAttempts, haltWhenTrue)(
          whenDone,
          whenTimeout,
          whenInterrupted
        )
      case (None, None) =>
        pollGlobalOperation(project, OperationName(operation.getName), delay, maxAttempts, haltWhenTrue)(
          whenDone,
          whenTimeout,
          whenInterrupted
        )
    }

  def pollZoneOperation[A](
    project: GoogleProject,
    zoneName: ZoneName,
    operationName: OperationName,
    delay: FiniteDuration,
    maxAttempts: Int,
    haltWhenTrue: Option[Stream[F, Boolean]]
  )(whenDone: F[A], whenTimeout: F[A], whenInterrupted: F[A])(implicit
    ev: Ask[F, TraceId]
  ): F[A] = {
    val op = getZoneOperation(project, zoneName, operationName)
    pollHelper(op, maxAttempts, delay, haltWhenTrue)(whenDone, whenTimeout, whenInterrupted)
  }

  def pollRegionOperation[A](
    project: GoogleProject,
    regionName: RegionName,
    operationName: OperationName,
    delay: FiniteDuration,
    maxAttempts: Int,
    haltWhenTrue: Option[Stream[F, Boolean]]
  )(whenDone: F[A], whenTimeout: F[A], whenInterrupted: F[A])(implicit
    ev: Ask[F, TraceId]
  ): F[A] = {
    val op = getRegionOperation(project, regionName, operationName)
    pollHelper(op, maxAttempts, delay, haltWhenTrue)(whenDone, whenTimeout, whenInterrupted)
  }

  def pollGlobalOperation[A](
    project: GoogleProject,
    operationName: OperationName,
    delay: FiniteDuration,
    maxAttempts: Int,
    haltWhenTrue: Option[Stream[F, Boolean]]
  )(whenDone: F[A], whenTimeout: F[A], whenInterrupted: F[A])(implicit
    ev: Ask[F, TraceId]
  ): F[A] = {
    val op = getGlobalOperation(project, operationName)
    pollHelper(op, maxAttempts, delay, haltWhenTrue)(whenDone, whenTimeout, whenInterrupted)
  }

  private[google2] def pollHelper[A](
    op: F[Operation],
    maxAttempts: Int,
    delay: FiniteDuration,
    haltWhenTrue: Option[Stream[F, Boolean]]
  )(whenDone: F[A], whenTimeout: F[A], whenInterrupted: F[A]): F[A] =
    for {
      op <- haltWhenTrue match {
        case Some(hwt) =>
          streamFUntilDone[F, Operation](op, maxAttempts, delay)
            .interruptWhen(hwt)
            .compile
            .lastOrError
        case None =>
          streamFUntilDone[F, Operation](op, maxAttempts, delay).compile.lastOrError
      }
      res <-
        if (op.isDone) {
          if (op.getError == com.google.cloud.compute.v1.Error.getDefaultInstance)
            whenDone
          else F.raiseError(PollError(op))
        } else {
          haltWhenTrue match {
            case Some(signal) =>
              signal.head.compile.last
                .flatMap { head =>
                  // If stream is interrupted, then at this point, haltWhenTrue will be either empty or contains `true`;
                  // If stream is never interrupted, then haltWhenTrue will always have value since it should an infinite stream
                  if (head.isEmpty || head.exists(identity))
                    whenInterrupted
                  else whenTimeout
                }
            case None =>
              whenTimeout
          }
        }
    } yield res
}

object ComputePollOperation {
  def resource[F[_]: StructuredLogger: Async](
    pathToCredential: String,
    blockerBound: Semaphore[F]
  ): Resource[F, ComputePollOperation[F]] =
    for {
      credential <- credentialResource(pathToCredential)
      scopedCredential = credential.createScoped(Seq(CLOUD_PLATFORM_SCOPE).asJava)
      interpreter <- resourceFromCredential(scopedCredential, blockerBound)
    } yield interpreter

  def resourceFromCredential[F[_]: StructuredLogger: Async](
    googleCredentials: GoogleCredentials,
    blockerBound: Semaphore[F]
  ): Resource[F, ComputePollOperation[F]] = {
    val credentialsProvider = FixedCredentialsProvider.create(googleCredentials)

    val zoneOperationSettings = ZoneOperationsSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val regionOperationSettings = RegionOperationsSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()
    val globalOperationSettings = GlobalOperationsSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)
      .build()

    for {
      zoneOperationClient <- backgroundResourceF(ZoneOperationsClient.create(zoneOperationSettings))
      regionOperationClient <- backgroundResourceF(RegionOperationsClient.create(regionOperationSettings))
      globalOperationClient <- backgroundResourceF(GlobalOperationsClient.create(globalOperationSettings))
    } yield new ComputePollOperationInterpreter[F](zoneOperationClient,
                                                   regionOperationClient,
                                                   globalOperationClient,
                                                   blockerBound
    )
  }
}

final case class PollError(operation: Operation) extends RuntimeException {
  override def getMessage: String = operation.getHttpErrorMessage
}
