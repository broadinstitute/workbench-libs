package org.broadinstitute.dsde.workbench.google2

import com.google.pubsub.v1.ProjectTopicName
import io.circe.{Decoder, Encoder}
import org.broadinstitute.dsde.workbench.model.TraceId

object JsonCodec {
  implicit val projectTopicNameEncoder: Encoder[ProjectTopicName] = Encoder.encodeString.contramap(
    projectTopicName => s"projects/${projectTopicName.getProject}/topics/${projectTopicName.getTopic}"
  )

  implicit val traceIdEncoder: Encoder[TraceId] = Encoder.encodeString.contramap(_.asString)

  implicit val traceIdDecoder: Decoder[TraceId] = Decoder.decodeString.map(s => TraceId(s))
}
