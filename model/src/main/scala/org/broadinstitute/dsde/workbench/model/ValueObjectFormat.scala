package org.broadinstitute.dsde.workbench.model

import java.nio.charset.StandardCharsets
import java.util.Base64

import spray.json.{DeserializationException, JsString, JsValue, RootJsonFormat}

import scala.util.Try

/**
  * Created by dvoet on 6/26/17.
  */
trait ValueObject {
  val value: String

  override def toString: String = value
}

/**
  * A ValueObject whose value can base 64 encoded/decoded.
  */
trait Base64Support { self: ValueObject =>
  private final val charset = StandardCharsets.UTF_8

  def decode: Option[String] = {
    Try(new String(Base64.getDecoder.decode(self.value.getBytes(charset)), charset)).toOption
  }

  def encode: String = {
    Base64.getEncoder.encodeToString(self.value.getBytes(charset))
  }
}

case class ValueObjectFormat[T <: ValueObject](create: String => T) extends RootJsonFormat[T] {
  def read(obj: JsValue): T = obj match {
    case JsString(value) => create(value)
    case _ => throw new DeserializationException("could not deserialize user object")
  }

  def write(obj: T): JsValue = JsString(obj.value)
}
