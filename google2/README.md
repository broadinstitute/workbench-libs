#Testing Locally

This is an example on how to test Google2 methods locally. In this example, we are testing the 
GoogleStorageInterpreter.getObjectMetadata method. Before this, we have downloaded a credential JSON 
file with which we can create a GoogleStorageService object.

`sbt:workbenchLibs>  sbt "project workbenchGoogle2" console`

[info] Set current project to workbench-google2

[info] Compiling 1 Scala source 

[info] Done compiling.
[info] Starting scala interpreter...

Welcome to Scala 2.12.8 (Java HotSpot(TM) 64-Bit Server VM, Java 1.9.3_172).
Type in expressions for evaluation. Or try :help.

```scala
// copy+paste to import all these 
import org.broadinstitute.dsde.workbench.google2.GoogleStorageService
import scala.concurrent.ExecutionContext.global
import cats.effect.IO
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
import org.broadinstitute.dsde.workbench.google2.GcsBlobName
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import cats.mtl.Ask
import java.util.UUID
import org.broadinstitute.dsde.workbench.model.TraceId
import cats.effect.Blocker
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}
import cats.effect.std.Semaphore
implicit val cs = IO.contextShift(global)
implicit val t = IO.timer(global)
implicit def logger = new ConsoleLogger("prefix-you-like", LogLevel(true, true, true, true))
implicit val traceId = Ask.const[IO, TraceId](TraceId(UUID.randomUUID()))
val blocker = Blocker.liftExecutionContext(global)
val blockerBound = Semaphore[IO](10).unsafeRunSync
```

`scala> GoogleStorageService.resource[IO]("credentials.json")`

SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
res0: cats.effect.Resource[cats.effect.IO,org.broadinstitute.dsde.workbench.google2.GoogleStorageService[cats.effect.IO]] = Bind(Bind(Allocate(<function1>),org.broadinstitute.dsde.workbench.google2.GoogleStorageInterpreter$$$Lambda$5201/1070179104@19a35b53),cats.effect.Resource$$Lambda$5203/746114085@43398b6)

`scala> res0.use(storage => storage.getObjectMetadata(GcsBucketName("bucket-name"), GcsBlobName("object-name"), None).compile.lastOrError)`

res2: cats.effect.IO[Map[String,String]] = IO$1818107578

`scala> res2.unsafeRunSync()`

res3: Map[String,String] = Map(passed -> true)
