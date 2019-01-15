package org.broadinstitute.dsde.workbench.google2

import com.google.pubsub.v1.ProjectTopicName
import fs2.Sink
import io.circe.Encoder

trait GooglePublisher[F[_]] {
  def createTopic(projectTopicName: ProjectTopicName): F[Unit]
  def publish[A: Encoder]: Sink[F, A]
}
