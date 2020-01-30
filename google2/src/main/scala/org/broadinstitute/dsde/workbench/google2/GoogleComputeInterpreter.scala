package org.broadinstitute.dsde.workbench.google2

import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, ContextShift, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.api.gax.rpc.ApiException
import com.google.api.gax.rpc.StatusCode.Code
import com.google.cloud.compute.v1._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.RetryConfig
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.concurrent.duration._
import scala.util.control.NonFatal

class GoogleComputeInterpreter[F[_]: Async: Logger: Timer: ContextShift](client: InstanceClient,
                                                                         retryConfig: RetryConfig,
                                                                         blocker: Blocker,
                                                                         blockerBound: Semaphore[F]) extends GoogleComputeService[F] {

  override def createInstance(project: GoogleProject, zone: ZoneName, instance: Instance)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
    val projectZone = ProjectZoneName.of(project.value, zone.value)
    blockingF(Async[F].delay(client.insertInstance(projectZone, instance)),
      s"com.google.cloud.compute.v1.InstanceClient.insertInstance(${projectZone.toString})"
    ).compile.lastOrError
  }

  override def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    blockingF(Async[F].delay(client.deleteInstance(projectZoneInstanceName)),
      s"com.google.cloud.compute.v1.InstanceClient.deleteInstance(${projectZoneInstanceName.toString})"
    ).compile.lastOrError
  }

  override def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Instance]] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    for {
      instanceAttempted <- blockingF(Async[F].delay(Option(client.getInstance(projectZoneInstanceName))),
        s"com.google.cloud.compute.v1.InstanceClient.getInstance(${projectZoneInstanceName.toString})"
      ).compile.lastOrError.attempt

      resEither = instanceAttempted.leftFlatMap {
        case e: ApiException if e.getStatusCode.getCode == Code.NOT_FOUND => Right(None)
        case e => Left(e)
      }
      result <- Async[F].fromEither(resEither)
    } yield result
  }

  override def stopInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    blockingF(Async[F].delay(client.stopInstance(projectZoneInstanceName)),
      s"com.google.cloud.compute.v1.InstanceClient.stopInstance(${projectZoneInstanceName.toString})"
    ).compile.lastOrError
  }

  override def startInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit ev: ApplicativeAsk[F, TraceId]): F[Operation] = {
    val projectZoneInstanceName = ProjectZoneInstanceName.of(instanceName.value, project.value, zone.value)
    blockingF(Async[F].delay(client.startInstance(projectZoneInstanceName)),
      s"com.google.cloud.compute.v1.InstanceClient.startInstance(${projectZoneInstanceName.toString})"
    ).compile.lastOrError
  }

  private def blockingF[A](fa: F[A], loggingMsg: String)(implicit ev: ApplicativeAsk[F, TraceId]): Stream[F, A] =
    Stream.eval(ev.ask).flatMap(traceId => retryGoogleF(retryConfig)(blockerBound.withPermit(blocker.blockOn(fa)), Some(traceId), loggingMsg))

}
object GoogleComputeInterpreter {
  val defaultRetryConfig = RetryConfig(
    org.broadinstitute.dsde.workbench.util2.addJitter(1 seconds, 1 seconds),
    x => x * 2,
    5,
    {
      case e: ApiException => e.isRetryable()
      case other => NonFatal(other)
    }
  )
}