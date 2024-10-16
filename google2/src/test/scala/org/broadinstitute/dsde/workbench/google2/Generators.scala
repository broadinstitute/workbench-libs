package org.broadinstitute.dsde.workbench
package google2

import java.nio.charset.Charset
import java.time.Instant
import cats.data.NonEmptyList
import com.google.pubsub.v1.TopicName
import org.broadinstitute.dsde.workbench.google2.DataprocRole.{Master, SecondaryWorker, Worker}
import org.broadinstitute.dsde.workbench.google2.GKEModels.{
  KubernetesClusterId,
  KubernetesClusterName,
  NodepoolId,
  NodepoolName
}
import org.broadinstitute.dsde.workbench.google2.NotificationEventTypes._
import org.broadinstitute.dsde.workbench.model.google._
import org.broadinstitute.dsde.workbench.util2.InstanceName
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
  val genDiskName = alphaLowerStrOfLength(10).map(DiskName)
  val genRegionName = for {
    usRegion <- Gen.oneOf("us-east1", "us-central1", "us-west1")
  } yield RegionName(usRegion)
  val genZoneName = for {
    zoneRegion <- genRegionName
    zoneLetter <- Gen.oneOf('a', 'b', 'c')
  } yield ZoneName(s"${zoneRegion.value}-$zoneLetter")
  val genLocation = for {
    zoneLetter <- Gen.oneOf('a', 'b', 'c')
  } yield Location(s"us-central1-$zoneLetter")
  val genKubernetesClusterId = for {
    project <- genGoogleProject
    location <- genLocation
    clusterName <- Gen.uuid.map(x => KubernetesClusterName(x.toString))
  } yield KubernetesClusterId(project, location, clusterName)
  val genNodepoolName = Gen.uuid.map(x => NodepoolName(x.toString))
  val genNodepoolId = for {
    clusterId <- genKubernetesClusterId
    nodepoolName <- genNodepoolName
  } yield NodepoolId(clusterId, nodepoolName)
  val genDataprocRole = Gen.oneOf(Master, Worker, SecondaryWorker)
  val genDataprocRoleZonePreemptibility = for {
    role <- genDataprocRole
    zone <- genZoneName
    isPreemptible <- if (role == SecondaryWorker) Gen.oneOf(true, false) else Gen.const(false)
  } yield DataprocRoleZonePreemptibility(role, zone, isPreemptible)
  val genDataprocRoleZonePreemptibilityInstancesTuple: Gen[(DataprocRoleZonePreemptibility, Set[InstanceName])] = for {
    dataprocRoleZonePreemptibility <- genDataprocRoleZonePreemptibility
    instanceNames <- Gen.listOfN(3, Gen.uuid.map(i => InstanceName(i.toString)))
  } yield (dataprocRoleZonePreemptibility, instanceNames.toSet)
  val genDataprocRoleZonePreemptibilityInstancesMap: Gen[Map[DataprocRoleZonePreemptibility, Set[InstanceName]]] =
    Gen.mapOfN(3, genDataprocRoleZonePreemptibilityInstancesTuple)

  def alphaLowerStrOfLength(n: Int): Gen[String] = Gen.listOfN(n, Gen.alphaLowerChar).map(_.mkString)

  implicit val arbProjectTopicName: Arbitrary[TopicName] = Arbitrary(genTopicName)
  implicit val arbNotificationResponse: Arbitrary[NotificationResponse] = Arbitrary(genNotificationResponse)
  implicit val arbNotificationRequest: Arbitrary[NotificationRequest] = Arbitrary(genNotificationRequest)
  implicit val arbDiskName: Arbitrary[DiskName] = Arbitrary(genDiskName)
  implicit val arbRegionName: Arbitrary[RegionName] = Arbitrary(genRegionName)
  implicit val arbZoneName: Arbitrary[ZoneName] = Arbitrary(genZoneName)
  implicit val arbLocation: Arbitrary[Location] = Arbitrary(genLocation)
  implicit val arbDataprocRoleZonePreemptibility: Arbitrary[DataprocRoleZonePreemptibility] = Arbitrary(
    genDataprocRoleZonePreemptibility
  )
  implicit val arbDataprocRoleZonePreemptibilityInstancesMap
    : Arbitrary[Map[DataprocRoleZonePreemptibility, Set[InstanceName]]] = Arbitrary(
    genDataprocRoleZonePreemptibilityInstancesMap
  )
}
