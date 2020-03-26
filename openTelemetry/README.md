#Testing Locally in sbt console

This is an example on how to test Google2 methods locally. In this example, we are testing the 
GoogleStorageInterpreter.getObjectMetadata method. Before this, we have downloaded a credential JSON 
file with which we can create a GoogleStorageService object.

`sbt:workbenchLibs>  sbt "project workbenchOpenTelemetry" console`

Copy paste the following code into console

```scala
import scala.concurrent.ExecutionContext.global
import cats.effect.IO
import java.nio.file.Paths
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetricsInterpreter
import scala.concurrent.duration._
import cats.effect.Blocker

implicit val cs = IO.contextShift(global)
implicit val t = IO.timer(global)
val blocker = Blocker.liftExecutionContext(global)

val resource = OpenTelemetryMetrics.resource[IO](Paths.get("<your google service account path>"), "test_app")
val res = resource.use {
  openTelemetry => 
    openTelemetry.incrementCounter("qi_counter", 1)
}

res.unsafeRunSync()
```

`scala> res0.unsafeRunSync()`