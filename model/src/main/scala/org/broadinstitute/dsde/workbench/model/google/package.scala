package org.broadinstitute.dsde.workbench.model

import java.util.UUID

import org.broadinstitute.dsde.workbench.model.google.GcsPathParser.GCS_SCHEME

package object google {
  def isServiceAccount(email: WorkbenchEmail): Boolean = {
    email.value.endsWith(".gserviceaccount.com")
  }

  def toAccountName(serviceAccountEmail: WorkbenchEmail): ServiceAccountName = {
    ServiceAccountName(serviceAccountEmail.value.split("@")(0))
  }

  def toUri(gcsPath: GcsPath): String = {
    s"$GCS_SCHEME://${gcsPath.bucketName.value}/${gcsPath.relativePath.value}"
  }

  def parseGcsPath(str: String): Either[GcsParseError, GcsPath] = {
    GcsPathParser.parseGcsPathFromString(str)
  }

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
