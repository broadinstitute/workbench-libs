package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.google.cloud.compute.v1.Operation
import fs2.Stream
import org.broadinstitute.dsde.workbench.google2.mock.MockComputePollOperation
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ComputePollOperationSpec extends AnyFlatSpec with Matchers with WorkbenchTestSuite {
  val computePollOperation = new MockComputePollOperation()

  // Ignore this test for now since it doesn't pass reliably in travis
  ignore should "handle interruption" in {
    val interruption = Stream.emits(List(false, false, true)).covary[IO].interleave(Stream.sleep_[IO](2 seconds))
    val op = Operation.newBuilder().setId("op").setName("opName").setTargetId("target").setStatus("PENDING").build()

    val res = computePollOperation.pollHelper(
      IO.pure(op),
      8,
      2 seconds,
      Some(interruption)
    )(
      IO(fail("this should be interrupted instead of completing")),
      IO(fail("this should be interrupted instead of timing out")),
      IO(succeed)
    )

    res.unsafeRunSync()
  }

  it should "handle timeout when interruption is defined" in {
    val interruption = (Stream.eval(IO.pure(false)) ++ Stream.sleep_[IO](1 seconds)).repeat
    val op = Operation.newBuilder().setId("op").setName("opName").setTargetId("target").setStatus("PENDING").build()

    val res = computePollOperation.pollHelper(
      IO.pure(op),
      3,
      1 seconds,
      Some(interruption)
    )(
      IO(fail("this should time out instead of completing")),
      IO(succeed),
      IO(fail("this should time out instead of interrupted"))
    )

    res.unsafeRunSync()
  }

  it should "handle timeout" in {
    val op = Operation.newBuilder().setId("op").setName("opName").setTargetId("target").setStatus("PENDING").build()

    val res = computePollOperation.pollHelper(
      IO.pure(op),
      3,
      1 seconds,
      None
    )(
      IO(fail("this should time out instead of completing")),
      IO(succeed),
      IO(fail("this should time out instead of interrupted"))
    )

    res.unsafeRunSync()
  }
}
