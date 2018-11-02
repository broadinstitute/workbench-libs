package org.broadinstitute.dsde.workbench

import java.io.FileInputStream

import cats.effect.{Resource, Sync}
import scala.concurrent.duration._

/**
 * Created by dvoet on 2/24/17.
 */
package object util {
  def toScalaDuration(javaDuration: java.time.Duration) = Duration.fromNanos(javaDuration.toNanos)

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
}
