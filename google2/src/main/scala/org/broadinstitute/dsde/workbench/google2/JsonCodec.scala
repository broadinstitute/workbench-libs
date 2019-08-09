package org.broadinstitute.dsde.workbench.google2

import com.google.pubsub.v1.ProjectTopicName
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.model.TraceId

object JsonCodec {
  implicit val projectTopicNameEncoder: Encoder[ProjectTopicName] = Encoder.encodeString.contramap(
    projectTopicName =>
      s"projects/${projectTopicName.getProject}/topics/${projectTopicName.getTopic}"
  )

  implicit val traceIdEncoder: Encoder[TraceId] = Encoder.encodeString.contramap(_.asString)
}
