package org.broadinstitute.dsde.workbench.service.test

import java.util.UUID

import scala.util.Random

trait RandomUtil {

  def randomUuid: String = {
    UUID.randomUUID().toString
  }
  
  /**
    * Make a random alpha-numeric (lowercase) string to be used as a semi-unique
    * identifier.
    *
    * @param length the number of characters in the string
    * @return a random string
    */
  def makeRandomId(length: Int = 7): String = {
    Random.alphanumeric.take(length).mkString.toLowerCase
  }

}
