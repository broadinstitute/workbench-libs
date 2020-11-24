package org.broadinstitute.dsde.workbench

import java.io.FileInputStream
import java.nio.file.Path

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync}
import io.circe.Decoder
import io.circe.fs2.decoder
import fs2.{io, Stream}
import org.typelevel.jawn.AsyncParser

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

package object util2 {
  def addJitter(baseTime: FiniteDuration, maxJitterToAdd: Duration): FiniteDuration =
    baseTime + ((scala.util.Random.nextFloat * maxJitterToAdd.toNanos) nanoseconds)

  def addJitter(baseTime: FiniteDuration): FiniteDuration =
    if (baseTime <= (10 seconds)) {
      addJitter(baseTime, baseTime * 0.1)
    } else {
      addJitter(baseTime, 1 second)
    }

  def readFile[F[_]](path: String)(implicit sf: Sync[F]): Resource[F, FileInputStream] =
    Resource.make(sf.delay(new FileInputStream(path)))(f => sf.delay(f.close()))

  def readPath[F[_]](path: Path)(implicit sf: Sync[F]): Resource[F, FileInputStream] =
    Resource.make(sf.delay(new FileInputStream(path.toString)))(f => sf.delay(f.close()))

  /*
   * Example:
   * scala> org.broadinstitute.dsde.workbench.util.readJsonFileToA[IO, List[String]](java.nio.file.Paths.get("/tmp/list"), None).compile.lastOrError.unsafeRunSync
   * res0: List[String] = List(this is great)
   *
   */
  def readJsonFileToA[F[_]: Sync: ContextShift: Concurrent, A: Decoder](path: Path,
                                                                        blocker: Option[Blocker]
  ): Stream[F, A] =
    io.file
      .readAll[F](path, blocker.getOrElse(Blocker.liftExecutionContext(global)), 4096)
      .through(fs2.text.utf8Decode)
      .through(_root_.io.circe.fs2.stringParser(AsyncParser.SingleValue))
      .through(decoder)
}
