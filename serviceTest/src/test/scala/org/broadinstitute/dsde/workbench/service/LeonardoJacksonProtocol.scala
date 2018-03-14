package org.broadinstitute.dsde.workbench.service

import java.net.URL

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind._
import org.broadinstitute.dsde.workbench.leonardo.model.google.{ClusterName, ClusterStatus, IP, OperationName}
import org.broadinstitute.dsde.workbench.leonardo.model.google.ClusterStatus.ClusterStatus
import org.broadinstitute.dsde.workbench.model.{ValueObject, WorkbenchEmail}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GcsPath, GoogleProject, parseGcsPath}
import spray.json.DeserializationException

trait LeonardoJacksonProtocol {

  // TODO: move to leoModel, workbenchModel, or serviceTests?
  lazy val leonardoModelModule: Module = {
    val module = new SimpleModule

    module.addSerializer(classOf[ClusterName], new ValueObjectSerializer[ClusterName])
    module.addDeserializer(classOf[ClusterName], ValueObjectDeserializer(ClusterName))

    module.addSerializer(classOf[GoogleProject], new ValueObjectSerializer[GoogleProject])
    module.addDeserializer(classOf[GoogleProject], ValueObjectDeserializer(GoogleProject))

    module.addSerializer(classOf[WorkbenchEmail], new ValueObjectSerializer[WorkbenchEmail])
    module.addDeserializer(classOf[WorkbenchEmail], ValueObjectDeserializer(WorkbenchEmail))

    module.addSerializer(classOf[OperationName], new ValueObjectSerializer[OperationName])
    module.addDeserializer(classOf[OperationName], ValueObjectDeserializer(OperationName))

    module.addSerializer(classOf[IP], new ValueObjectSerializer[IP])
    module.addDeserializer(classOf[IP], ValueObjectDeserializer(IP))

    module.addSerializer(classOf[GcsBucketName], new ValueObjectSerializer[GcsBucketName])
    module.addDeserializer(classOf[GcsBucketName], ValueObjectDeserializer(GcsBucketName))

    module.addSerializer(classOf[ClusterStatus], ClusterStatusSerializer)
    module.addDeserializer(classOf[ClusterStatus], ClusterStatusDeserializer)

    module.addSerializer(classOf[URL], URLSerializer)
    module.addDeserializer(classOf[URL], URLDeserializer)

    module.addSerializer(classOf[GcsPath], GcsPathSerializer)
    module.addDeserializer(classOf[GcsPath], GcsPathDeserializer)

    module
  }

  // TODO: move to leoModel, workbenchModel, or serviceTests?

  private class ValueObjectSerializer[T <: ValueObject] extends JsonSerializer[T] {
    override def serialize(obj: T, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      gen.writeString(obj.value)
    }
  }

  private case class ValueObjectDeserializer[T <: ValueObject](create: String => T) extends JsonDeserializer[T] {
    override def deserialize(parser: JsonParser, ctx: DeserializationContext): T = {
      create(parser.getValueAsString)
    }
  }

  private case object ClusterStatusSerializer extends JsonSerializer[ClusterStatus] {
    override def serialize(status: ClusterStatus, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      gen.writeString(status.toString)
    }
  }

  private case object ClusterStatusDeserializer extends JsonDeserializer[ClusterStatus] {
    override def deserialize(parser: JsonParser, ctx: DeserializationContext): ClusterStatus = {
      ClusterStatus.withName(parser.getValueAsString)
    }
  }

  private case object URLSerializer extends JsonSerializer[URL] {
    override def serialize(url: URL, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      gen.writeString(url.toString)
    }
  }

  private case object URLDeserializer extends JsonDeserializer[URL] {
    override def deserialize(parser: JsonParser, ctx: DeserializationContext): URL = {
      new URL(parser.getValueAsString)
    }
  }

  private case object GcsPathSerializer extends JsonSerializer[GcsPath] {
    override def serialize(path: GcsPath, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      gen.writeString(path.toUri)
    }
  }

  private case object GcsPathDeserializer extends JsonDeserializer[GcsPath] {
    override def deserialize(parser: JsonParser, ctx: DeserializationContext): GcsPath = {
      val uri = parser.getValueAsString
      // TODO add these checks elsewhere
      parseGcsPath(uri).getOrElse(throw DeserializationException(s"Could not parse bucket URI from: $uri"))
    }
  }


}
