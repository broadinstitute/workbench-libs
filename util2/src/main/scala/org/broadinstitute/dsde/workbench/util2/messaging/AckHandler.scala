package org.broadinstitute.dsde.workbench.util2.messaging

trait AckHandler {
  def ack(): Unit
  def nack(): Unit
}
