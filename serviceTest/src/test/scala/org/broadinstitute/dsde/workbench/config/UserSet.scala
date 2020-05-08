package org.broadinstitute.dsde.workbench.config

import scala.util.Random

/**
 * Set of users mapping name -> Credential
 * Used by UserPool to select a user for a particular function
 */
case class UserSet(userMap: Map[String, Credentials]) {
  def getUserCredential(username: String): Credentials =
    userMap(username)

  def getRandomCredentials(n: Int): Seq[Credentials] =
    Random.shuffle(userMap.values.toVector).take(n)
}
