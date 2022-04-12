package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import com.google.cloud.dataproc.v1.{
  Cluster,
  ClusterConfig,
  ClusterControllerClient,
  ClusterStatus,
  GceClusterConfig,
  InstanceGroupConfig
}
import org.broadinstitute.dsde.workbench.google2.DataprocRole.{Master, SecondaryWorker, Worker}
import org.broadinstitute.dsde.workbench.google2.Generators.genGoogleProject
import org.broadinstitute.dsde.workbench.google2.mock.FakeGoogleComputeService
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel, PropertyBasedTesting, WorkbenchTestSuite}
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.BDDMockito.`given`
import org.mockito.{MockSettings, Mockito}
import org.mockito.Mockito.{doThrow, mock, spy, when, withSettings, RETURNS_SMART_NULLS}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import scala.jdk.CollectionConverters._

class GoogleDataprocInterpreterSpec
    extends AnyFlatSpecLike
    with Matchers
    with WorkbenchTestSuite
    with PropertyBasedTesting
    with MockitoSugar {
  "getAllInstanceNames" should "return instances for a cluster correctly" in {
    val cluster = Cluster
      .newBuilder()
      .setConfig(
        ClusterConfig
          .newBuilder()
          .setGceClusterConfig(
            GceClusterConfig
              .newBuilder()
              .setZoneUri("https://www.googleapis.com/compute/v1/projects/some-project/zones/us-central1-a")
          )
          .setMasterConfig(InstanceGroupConfig.newBuilder().addInstanceNames("master"))
          .setWorkerConfig(
            InstanceGroupConfig.newBuilder().addAllInstanceNames(List("worker0", "worker1", "worker2").asJava)
          )
          .setSecondaryWorkerConfig(
            InstanceGroupConfig
              .newBuilder()
              .setIsPreemptible(true)
              .addAllInstanceNames(List("secondaryWorker0", "secondaryWorker1").asJava)
          )
      )
      .build()

    val res = GoogleDataprocInterpreter.getAllInstanceNames(cluster)
    val expectedResult = Map(
      DataprocRoleZonePreemptibility(Master, ZoneName("us-central1-a"), false) -> Set(InstanceName("master")),
      DataprocRoleZonePreemptibility(Worker, ZoneName("us-central1-a"), false) -> Set(InstanceName("worker0"),
                                                                                      InstanceName("worker1"),
                                                                                      InstanceName("worker2")
      ),
      DataprocRoleZonePreemptibility(SecondaryWorker, ZoneName("us-central1-a"), true) -> Set(
        InstanceName("secondaryWorker0"),
        InstanceName("secondaryWorker1")
      )
    )
    res shouldBe expectedResult
  }

  "countPreemptibles" should "count preemptibles correctly" in {
    val singleNode = Map(
      DataprocRoleZonePreemptibility(Master, ZoneName("us-central1-a"), false) -> Set(InstanceName("master"))
    )
    GoogleDataprocInterpreter.countPreemptibles(singleNode) shouldBe 0

    val workersNoPreemptibles = Map(
      DataprocRoleZonePreemptibility(Master, ZoneName("us-central1-a"), false) -> Set(InstanceName("master")),
      DataprocRoleZonePreemptibility(Worker, ZoneName("us-central1-a"), false) -> Set(InstanceName("worker1"),
                                                                                      InstanceName("worker2")
      )
    )
    GoogleDataprocInterpreter.countPreemptibles(workersNoPreemptibles) shouldBe 0

    val workersAndPreemptibles = Map(
      DataprocRoleZonePreemptibility(Master, ZoneName("us-central1-a"), false) -> Set(InstanceName("master")),
      DataprocRoleZonePreemptibility(Worker, ZoneName("us-central1-a"), false) -> Set(InstanceName("worker1"),
                                                                                      InstanceName("worker2")
      ),
      DataprocRoleZonePreemptibility(Worker, ZoneName("us-central1-a"), true) -> Set(InstanceName("preemptible1"),
                                                                                     InstanceName("preemptible2"),
                                                                                     InstanceName("preemptible3"),
                                                                                     InstanceName("preemptible4"),
                                                                                     InstanceName("preemptible5")
      )
    )
    GoogleDataprocInterpreter.countPreemptibles(workersAndPreemptibles) shouldBe 5
  }

  it should "not attempt to stop a cluster if the cluster is already STOPPED" in ioAssertion {
    val region = RegionName("us-central-1")
    val mockClusterClient: ClusterControllerClient = mock[ClusterControllerClient]
    val cluster = Cluster
      .newBuilder()
      .setStatus(ClusterStatus.newBuilder().setState(com.google.cloud.dataproc.v1.ClusterStatus.State.STOPPED))
      .build()
    when(mockClusterClient.getCluster(any[String], any[String], any[String]))
      .thenReturn(cluster)

    implicit val traceId = Ask.const[IO, TraceId](TraceId("123"))
    val googleProject = genGoogleProject.sample.get
    val dataprocClusterName = DataprocClusterName("cluster1")

    for {
      semaphore <- Semaphore[IO](10)
      interp = new GoogleDataprocInterpreter[IO](
        Map(region -> mockClusterClient),
        FakeGoogleComputeService,
        semaphore,
        RetryPredicates.standardGoogleRetryConfig
      )
      res <- interp.stopCluster(googleProject, region, dataprocClusterName, None, true)
    } yield res shouldBe None
  }
}
