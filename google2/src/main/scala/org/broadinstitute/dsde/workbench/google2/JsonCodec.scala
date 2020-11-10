package org.broadinstitute.dsde.workbench.google2

import cats.implicits._
import com.google.pubsub.v1.TopicName
import io.circe.{Decoder, Encoder}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

object JsonCodec {
  implicit val topicNameEncoder: Encoder[TopicName] = Encoder.encodeString.contramap(
    tn => TopicName.format(tn.getProject, tn.getTopic)
  )

  implicit val projectTopicNameDecoder: Decoder[TopicName] = Decoder.decodeString.emap { s =>
    // topic has this format: '//pubsub.googleapis.com/projects/{project-identifier}/topics/{my-topic}'
    Either.catchNonFatal(TopicName.parse(s)).leftMap(t => t.getMessage)
  }

  implicit val traceIdEncoder: Encoder[TraceId] = Encoder.encodeString.contramap(_.asString)

  implicit val traceIdDecoder: Decoder[TraceId] = Decoder.decodeString.map(s => TraceId(s))

  implicit val googleProjectEncoder: Encoder[GoogleProject] = Encoder.encodeString.contramap(_.value)
}
