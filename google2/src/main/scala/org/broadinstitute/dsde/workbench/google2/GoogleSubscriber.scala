package org.broadinstitute.dsde.workbench.google2

trait GoogleSubscriber[F[_]] {
  def start: F[Unit]
  def stop: F[Unit]
}