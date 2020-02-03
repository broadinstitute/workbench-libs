package org.broadinstitute.dsde.workbench.google2

import io.circe.parser._
import org.broadinstitute.dsde.workbench.google2.Generators._
import org.broadinstitute.dsde.workbench.google2.GoogleServiceHttpInterpreter._
import org.broadinstitute.dsde.workbench.util2.{PropertyBasedTesting, WorkbenchTestSuite}
import org.scalatest.{FlatSpec, Matchers}
import io.circe.syntax._

class GoogleStorageNotificationCreatorInterpreterSpec
    extends FlatSpec
    with Matchers
    with WorkbenchTestSuite
    with PropertyBasedTesting {
  "notificationResponseDecoder" should "decode NotificationResopnse properly" in {
    forAll { (notificationResponse: NotificationResponse) =>
      val jsonString = notificationResponse.items
        .map { notifications =>
          val topics = notifications.map(n => s"""
                                                 | {
                                                 |   "topic" : "//pubsub.googleapis.com/projects/${n.topic.getProject}/topics/${n.topic.getTopic}"
                                                 | }
              """.stripMargin)

          s"""
             |{ "items": [${topics.toList.mkString(",")}]}
             """.stripMargin
        }
        .getOrElse("{}")

      val result = for {
        json <- parse(jsonString)
        r <- json.as[NotificationResponse]
      } yield r
      result shouldBe (Right(notificationResponse))
    }
  }

  "notificationRequestEnoder" should "encode NotificationRequest properly" in {
    forAll { (notificationRequest: NotificationRequest) =>
      val eventTypesString =
        if (notificationRequest.eventTypes.isEmpty)
          ""
        else
          notificationRequest.eventTypes.map(_.asString).mkString("\"", "\",\"", "\"")
      val eventTypes = s""" "event_types": [$eventTypesString]"""
      val objectNamePrefix = s""" "object_name_prefix": ${notificationRequest.objectNamePrefix
        .map(s => "\"" + s + "\"")
        .getOrElse("null")} """
      val expectedJsonString =
        s"""
           |{
           |  "payload_format": "JSON_API_V1",
           |  "topic": "projects/${notificationRequest.topic.getProject}/topics/${notificationRequest.topic.getTopic}",
           |  $objectNamePrefix,
           |  $eventTypes
           |}
          """.stripMargin

      val expectedJson =
        parse(expectedJsonString).getOrElse(throw new Exception("failed to parse NotificationRequest json"))

      val result = notificationRequest.asJson
      result shouldBe (expectedJson)
    }
  }
}
