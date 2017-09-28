package org.broadinstitute.dsde.workbench.google

import java.util.UUID

/**
  * Created by rtitle on 9/28/17.
  */
package object gcs {
  /**
    * Generates a unique bucket name with the given prefix. The prefix may be
    * modified so that it adheres to bucket naming rules specified here:
    *
    * https://cloud.google.com/storage/docs/naming.
    *
    * The resulting bucket name is guaranteed to be a valid bucket name.
    *
    * @param prefix bucket name prefix
    * @return generated bucket name
    */
  def generateUniqueBucketName(prefix: String) = {
    // may only contain lowercase letters, numbers, underscores, dashes, or dots
    val lowerCaseName = prefix.toLowerCase.filter { c =>
      Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.'
    }

    // must start with a letter or number
    val sb = new StringBuilder(lowerCaseName)
    if (!Character.isLetterOrDigit(sb.head)) sb.setCharAt(0, '0')

    // max length of 63 chars, including the uuid
    val uuid = UUID.randomUUID.toString
    val maxNameLength = 63 - uuid.length - 1
    if (sb.length > maxNameLength) sb.setLength(maxNameLength)

    // must not start with "goog" or contain the string "google"
    val processedName = sb.replaceAllLiterally("goog", "g00g")

    GcsBucketName(s"$processedName-$uuid")
  }
}
