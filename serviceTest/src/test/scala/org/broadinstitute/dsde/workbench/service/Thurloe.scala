package org.broadinstitute.dsde.workbench.service

import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.auth.AuthToken

import scala.util.Try

/**
 */
trait Thurloe extends RestClient with LazyLogging {

  val url = ServiceTestConfig.FireCloud.thurloeApiUrl
  val fireCloudId = ServiceTestConfig.FireCloud.fireCloudId
  private val thurloeHeaders = List(FireCloudIdHeader(fireCloudId))

  object FireCloudIdHeader extends ModeledCustomHeaderCompanion[FireCloudIdHeader] {
    override def name = "X-FireCloud-Id"
    override def parse(value: String) = Try(new FireCloudIdHeader(value))
  }
  case class FireCloudIdHeader(id: String) extends ModeledCustomHeader[FireCloudIdHeader] {
    override def companion: ModeledCustomHeaderCompanion[FireCloudIdHeader] = FireCloudIdHeader
    override def renderInRequests = true
    override def renderInResponses = false
    override def value: String = id
  }

  object keyValuePairs {

    def delete(subjectId: String, key: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting $key for $subjectId")
      deleteRequest(url + s"api/thurloe/$subjectId/$key", thurloeHeaders)
    }

    def deleteAll(subjectId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting all key/value pairs from for $subjectId")
      getAll(subjectId).foreach[Unit] { case (key, _) =>
        delete(subjectId, key)
      }
    }

    def set(subjectId: String, key: String, value: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Setting $key as $value for $subjectId")
      postRequest(url + s"api/thurloe",
                  Map("userId" -> subjectId, "keyValuePairs" -> List(Map("key" -> key, "value" -> value))),
                  thurloeHeaders
      )
    }

    def getAll(subjectId: String)(implicit token: AuthToken): Map[String, String] = {

      def extractTupleFromKeyValueMap(m: Map[String, String]): Option[(String, String)] =
        (m.get("key"), m.get("value")) match {
          case (Some(k), Some(v)) => Some(k -> v)
          case _                  => None
        }

      val response: String = parseResponse(getRequest(url + s"api/thurloe/$subjectId", thurloeHeaders))

      /*
       * "keyValuePairs" contains a list of maps, each with 2 entries: one for
       * the key and one for the value. To make it easier to work with, we need
       * to turn this:
       *
       *   List(
       *     Map("key" -> "foo1", "value" -> "bar1"),
       *     Map("key" -> "foo2", "value" -> "bar2"))
       *
       * into this:
       *
       *   Map("foo1" -> "bar1", "foo2" -> "bar2")
       *
       */
      response.fromJsonMapAs[List[Map[String, String]]]("keyValuePairs") match {
        case Some(maps) => maps.flatMap(m => extractTupleFromKeyValueMap(m)).toMap
        case None       => Map()
      }
    }
  }
}

object Thurloe extends Thurloe
