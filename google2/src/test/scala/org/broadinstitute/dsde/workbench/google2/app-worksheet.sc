// This file will allow you to run methods in the google2.Test class to test changes manually

import cats.effect.unsafe.implicits.global
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}

val testObj = new org.broadinstitute.dsde.workbench.google2.Test()
implicit def logger: ConsoleLogger = new ConsoleLogger("manual", LogLevel(true, true, true, true))

val patchOp = testObj.patchReplicas()

patchOp.unsafeRunSync()
