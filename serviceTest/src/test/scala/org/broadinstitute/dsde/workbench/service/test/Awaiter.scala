package org.broadinstitute.dsde.workbench.service.test

trait Awaiter {
  def awaitReady(): Unit
}