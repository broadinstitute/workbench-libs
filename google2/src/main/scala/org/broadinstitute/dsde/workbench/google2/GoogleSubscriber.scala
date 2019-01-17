package org.broadinstitute.dsde.workbench.google2

import fs2.Stream

trait GoogleSubscriber[F[_], A] {
  def messages: Stream[F, Event[A]]
  def start: F[Unit]
  def stop: F[Unit]
}