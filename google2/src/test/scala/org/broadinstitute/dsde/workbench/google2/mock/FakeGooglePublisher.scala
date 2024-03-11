package org.broadinstitute.dsde.workbench.google2.mock

import cats.effect.IO
import cats.mtl.Ask
import com.google.pubsub.v1.PubsubMessage
import fs2.Pipe
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.google2.GooglePublisher
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.messaging.CloudPublisher

class FakeGooglePublisher extends GooglePublisher[IO] with CloudPublisher[IO] {
  override def publish[MessageType: Encoder]: Pipe[IO, MessageType, Unit] =
    in => in.evalMap(_ => IO.unit)

  override def publishNative: Pipe[IO, PubsubMessage, Unit] = in => in.evalMap(_ => IO.unit)

  override def publishString: Pipe[IO, String, Unit] = in => in.evalMap(_ => IO.unit)

  override def publishOne[MessageType: Encoder](message: MessageType)(implicit ev: Ask[IO, TraceId]): IO[Unit] = IO.unit
  override def publishOne[MessageType: Encoder](message: MessageType,
                                                messageAttributes: Map[String, String] = Map.empty
  )(implicit ev: Ask[IO, TraceId]): IO[Unit] = IO.unit

  override def publishNativeOne(message: PubsubMessage): IO[Unit] = IO.unit
}
