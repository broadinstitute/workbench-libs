package org.broadinstitute.dsde.workbench.google2

import com.google.cloud.dataproc.v1.{Cluster, ClusterConfig, InstanceGroupConfig}
import org.broadinstitute.dsde.workbench.util2.{PropertyBasedTesting, WorkbenchTestSuite}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import scala.collection.JavaConverters._

class GoogleDataprocInterpreterSpec
    extends AnyFlatSpecLike
    with Matchers
    with WorkbenchTestSuite
    with PropertyBasedTesting {
  "getAllInstanceNames" should "return instances for a cluster correctly" in {
    val cluster = Cluster
      .newBuilder()
      .setConfig(
        ClusterConfig
          .newBuilder()
          .setMasterConfig(InstanceGroupConfig.newBuilder().addInstanceNames("master"))
          .setWorkerConfig(
            InstanceGroupConfig.newBuilder().addAllInstanceNames(List("instance0", "instance1", "instance2").asJava)
          )
      )
      .build()

    val res = GoogleDataprocInterpreter.getAllInstanceNames(cluster)
    val expectedResult = Map(
      DataprocRole.Master -> Set(InstanceName("master")),
      DataprocRole.Worker -> Set(InstanceName("instance0"), InstanceName("instance1"), InstanceName("instance2"))
    )
    res shouldBe expectedResult
  }
}
