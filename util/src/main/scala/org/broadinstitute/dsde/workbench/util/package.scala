package org.broadinstitute.dsde.workbench

import java.io.FileInputStream
import java.nio.file.Path

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import io.circe.Decoder
import io.circe.fs2.decoder
import fs2.{Stream, io}
import org.typelevel.jawn.AsyncParser

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

/**
 * Created by dvoet on 2/24/17.
 */
package object util {
  def toScalaDuration(javaDuration: java.time.Duration): FiniteDuration = Duration.fromNanos(javaDuration.toNanos)

  def addJitter(baseTime: FiniteDuration, maxJitterToAdd: Duration): FiniteDuration = {
    baseTime + ((scala.util.Random.nextFloat * maxJitterToAdd.toNanos) nanoseconds)
  }

  def addJitter(baseTime: FiniteDuration): FiniteDuration = {
    if(baseTime <= (10 seconds)) {
      addJitter(baseTime, baseTime * 0.1)
    } else {
      addJitter(baseTime, 1 second)
    }
  }

  /**
    * Converts a `java.util.Map.Entry` to a `scala.Tuple2`
    */
  implicit class JavaEntrySupport[A, B](entry: java.util.Map.Entry[A, B]) {
    def toTuple: (A, B) = (entry.getKey, entry.getValue)
  }

  def readFile[F[_]](path: String)(implicit sf: Sync[F]): Resource[F, FileInputStream] =
    Resource.make(sf.delay(new FileInputStream(path)))(f => sf.delay(f.close()))

  /*
   * Example:
   * scala> org.broadinstitute.dsde.workbench.util.readJsonFileToA[IO, List[String]](java.nio.file.Paths.get("/tmp/list"), None).compile.lastOrError.unsafeRunSync
   * res0: List[String] = List(this is great)
   *
   */
  def readJsonFileToA[F[_]: Sync: ContextShift: Concurrent, A: Decoder](path: Path, blockingExecutionContext: Option[ExecutionContext] = None): Stream[F, A] = {
    io.file.readAll[F](path, blockingExecutionContext.getOrElse(global), 4096)
      .through(fs2.text.utf8Decode)
      .through(_root_.io.circe.fs2.stringParser(AsyncParser.SingleValue))
      .through(decoder)
  }
}
