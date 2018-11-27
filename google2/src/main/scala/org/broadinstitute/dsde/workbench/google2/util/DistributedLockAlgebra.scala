package org.broadinstitute.dsde.workbench.google2
package util

import cats.effect.Resource

trait DistributedLockAlgebra[F[_]] {
  def withLock(lock: LockPath): Resource[F, Unit]
}
