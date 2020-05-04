package org.broadinstitute.dsde.workbench.google2

import java.nio.charset.Charset
import java.time.Instant

import cats.data.NonEmptyList
import com.google.pubsub.v1.TopicName
import org.broadinstitute.dsde.workbench.google2.NotificationEventTypes._
import org.broadinstitute.dsde.workbench.model.google._
import org.scalacheck.{Arbitrary, Gen}

object Generators {
  val utf8Charset = Charset.forName("UTF-8")
  val genGoogleProject: Gen[GoogleProject] = Gen.uuid.map(x => GoogleProject(s"project${x.toString}"))
  val genGcsBucketName: Gen[GcsBucketName] = Gen.uuid.map(x => GcsBucketName(s"bucket${x.toString}"))
  val genGcsBlobName: Gen[GcsBlobName] = Gen.uuid.map(x => GcsBlobName(s"object${x.toString}"))
  val genGcsObjectName: Gen[GcsObjectName] = for {
    blobName <- genGcsBlobName
    createdTime <- Gen.calendar.map(x => Instant.ofEpochMilli(x.getTimeInMillis))
  } yield GcsObjectName(blobName.value, createdTime)
  val genGcsObjectBody: Gen[Array[Byte]] = Gen.alphaStr.map(x => x.getBytes(utf8Charset))
  val genPerson: Gen[Person] = for {
    name <- Gen.alphaStr.map(s => s"name$s")
    email <- Gen.alphaStr.map(s => s"email$s")
  } yield Person(name, email)
  val genListPerson = Gen.nonEmptyListOf(genPerson)
  val genTopicName: Gen[TopicName] = for {
    project <- genGoogleProject
    topic <- Gen.alphaStr.map(x => s"topic$x")
  } yield TopicName.of(project.value, topic)
  val genNotificationResponse = Gen.listOf[TopicName](genTopicName).map { topics =>
    val notifications = topics.map(t => Notification(t))
    NotificationResponse(NonEmptyList.fromList(notifications))
  }
  val genNotificationEventTypes = Gen.someOf(ObjectFinalize, ObjectMedataUpdate, ObjectDelete, ObjectArchive)
  val genFilters = for {
    objectNamePrefix <- Gen.alphaStr
    eventTypes <- genNotificationEventTypes
  } yield {
    val prefix = if (objectNamePrefix.isEmpty) None else Some(objectNamePrefix)
    Filters(eventTypes.toList, prefix)
  }
  val genNotificationRequest = for {
    topic <- genTopicName
    filters <- genFilters
  } yield NotificationRequest(topic, "JSON_API_V1", filters.eventTypes, filters.objectNamePrefix)

  implicit val arbProjectTopicName: Arbitrary[TopicName] = Arbitrary(genTopicName)
  implicit val arbNotificationResponse: Arbitrary[NotificationResponse] = Arbitrary(genNotificationResponse)
  implicit val arbNotificationRequest: Arbitrary[NotificationRequest] = Arbitrary(genNotificationRequest)

}
