package org.broadinstitute.dsde.workbench.model

import java.util.UUID
import org.broadinstitute.dsde.workbench.model.google.GcsPathParser._

package object google {
  def isServiceAccount(email: WorkbenchEmail): Boolean =
    email.value.endsWith(".gserviceaccount.com")

  def toAccountName(serviceAccountEmail: WorkbenchEmail): ServiceAccountName =
    ServiceAccountName(serviceAccountEmail.value.split("@")(0))

  implicit class GcsPathSupport(gcsPath: GcsPath) {
    def toUri: String =
      s"$GCS_SCHEME://${gcsPath.bucketName.value}/${gcsPath.objectName.value}"
  }

  def parseGcsPath(str: String): Either[GcsParseError, GcsPath] =
    GcsPathParser.parseGcsPathFromString(str)

  /**
   * Generates a unique bucket name with the given prefix. The prefix may be
   * modified so that it adheres to bucket naming rules specified here:
   *
   * https://cloud.google.com/storage/docs/naming.
   *
   * If trimPrefix is true, you're guaranteed the resulting bucket name will be unique.
   * If trimPrefix is false, your bucket name may not be unique if your prefix is >=64 chars.
   * but we'll keep as much of your prefix as possible.
   *
   * @param prefix bucket name prefix
   * @return generated bucket name
   */
  def generateUniqueBucketName(prefix: String, trimPrefix: Boolean = true): GcsBucketName = {
    // may only contain lowercase letters, numbers, underscores, dashes, or dots
    val lowerCaseName = prefix.toLowerCase.filter { c =>
      Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.'
    }

    //maximum length is 63. Then we're going to shove a dash in between the prefix and the uuid, so 62.
    val maxBucketNameLen = 62

    // must start with a letter or number
    val sb = new StringBuilder(lowerCaseName)
    if (!Character.isLetterOrDigit(sb.head)) sb.setCharAt(0, '0')

    val uuid = UUID.randomUUID.toString

    //if we're trimming the prefix, we make the prefix short enough to accommodate the UUID.
    //if we're trimming the UUID, we might still need to trim the prefix anyway if it's enormous.
    val trimmedPrefix = if (trimPrefix) sb.take(maxBucketNameLen - uuid.length) else sb.take(maxBucketNameLen)
    val trimmedUUID =
      if (trimPrefix) uuid else uuid.take(Math.min(maxBucketNameLen - trimmedPrefix.length, uuid.length))

    // must not start with "goog" or contain the string "google"
    // This implementation is a bit ugly, but it's to work around 2.12 and 2.13 compatibility issue
    def go(originalString: StringBuilder): StringBuilder = originalString.indexOf("goog") match {
      case -1 => originalString
      case index =>
        go(originalString.replace(index, index + 3, "g00"))
    }

    val processedName = go(trimmedPrefix)

    GcsBucketName(s"$processedName-$trimmedUUID")
  }
}
