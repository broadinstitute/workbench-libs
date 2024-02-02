package org.broadinstitute.dsde.workbench.util2.messaging

/**
 * Wrapper around the message's ack and nack methods of the underlying messaging system.
 */
trait AckHandler {

  /**
   * Acknowledges the message.
   */
  def ack(): Unit

  /**
   * Negative-acknowledges the message.
   */
  def nack(): Unit
}
