package org.broadinstitute.dsde.workbench.google2

import cats.effect.{Async, Resource}
import cats.mtl.Ask
import com.google.pubsub.v1.PubsubMessage
import fs2.Pipe
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.messaging.CloudPublisher
import org.typelevel.log4cats.StructuredLogger

trait GooglePublisher[F[_]] extends CloudPublisher[F] {

  /**
   * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
   */
  def publish[MessageType: Encoder]: Pipe[F, MessageType, Unit]

  /**
   * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
   */
  def publishNative: Pipe[F, PubsubMessage, Unit]

  /**
   * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
   */
  def publishNativeOne(message: PubsubMessage): F[Unit]

  /**
   * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
   * This publishes a single message, but the preferred approach is with streams via `publish`
   */
  def publishOne[MessageType: Encoder](message: MessageType)(implicit ev: Ask[F, TraceId]): F[Unit]

  /**
   * Watch out message size quota and limitations https://cloud.google.com/pubsub/quotas
   */
  def publishString: Pipe[F, String, Unit]

}

object GooglePublisher {
  @deprecated("Use cloudPublisherResource instead", "0.36")
  def resource[F[_]: Async: StructuredLogger](
    config: PublisherConfig
  ): Resource[F, GooglePublisher[F]] =
    for {
      publisher <- GooglePublisherInterpreter.publisher(config)
    } yield GooglePublisherInterpreter(publisher)

  def cloudPublisherResource[F[_]: Async: StructuredLogger](
    config: PublisherConfig
  ): Resource[F, CloudPublisher[F]] =
    for {
      publisher <- GooglePublisherInterpreter.publisher(config)
    } yield GooglePublisherInterpreter(publisher)
}
