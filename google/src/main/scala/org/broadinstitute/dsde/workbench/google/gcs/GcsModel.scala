package org.broadinstitute.dsde.workbench.google.gcs

import com.google.common.net.UrlEscapers
import java.net.URI
import java.util.UUID
import org.broadinstitute.dsde.workbench.google.gcs.GcsPathParser._
import scala.util.Try

/** A GCS path including bucket name and path */
case class GcsPath(bucketName: GcsBucketName, relativePath: GcsRelativePath) {
  def toUri: String = s"$GCS_SCHEME://${bucketName.name}/${relativePath.name}"
}
object GcsPath {
  def parse(str: String): Either[GcsParseError, GcsPath] =
    GcsPathParser.parseGcsPathFromString(str)
}

/** A GCS relative path */
case class GcsRelativePath(name: String) extends AnyVal

/** A valid GCS bucket name */
case class GcsBucketName(name: String) extends AnyVal
object GcsBucketName {
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

case class GcsParseError(message: String) extends AnyVal

private object GcsPathParser {
  final val GCS_SCHEME = "gs"

  /*
   * Provides some level of validation of GCS bucket names.
   * See https://cloud.google.com/storage/docs/naming for full spec
   */
  final val GCS_BUCKET_NAME_PATTERN_BASE = """[a-z0-9][a-z0-9-_\\.]{1,61}[a-z0-9]"""
  final val GCS_BUCKET_NAME_PATTERN = s"""^$GCS_BUCKET_NAME_PATTERN_BASE$$""".r

  /*
   * Regex for a full GCS path which captures the bucket name.
   */
  final val GCS_PATH_PATTERN =
    s"""
      (?x)                                      # Turn on comments and whitespace insensitivity
      ^${GCS_SCHEME}://
      (                                         # Begin capturing group for gcs bucket name
        $GCS_BUCKET_NAME_PATTERN_BASE           # Regex for bucket name - soft validation, see comment above
      )                                         # End capturing group for gcs bucket name
      /
      (?:
        .*                                      # No validation here
      )?
    """.trim.r


  def parseGcsPathFromString(path: String): Either[GcsParseError, GcsPath] = {
    for {
      uri <- parseAsUri(path)
      _ <- validateScheme(uri)
      host <- getAndValidateHost(path, uri)
      relativePath <- getAndValidateRelativePath(uri)
    } yield GcsPath(host, relativePath)
  }

  def parseAsUri(path: String): Either[GcsParseError, URI] = {
    Try {
      URI.create(UrlEscapers.urlFragmentEscaper().escape(path))
    }.toEither.left.map { e =>
      GcsParseError(s"Unparseable GCS path: ${e.getMessage}")
    }
  }

  def validateScheme(uri: URI): Either[GcsParseError, Unit] = {
    // Allow null or gs:// scheme
    if (uri.getScheme == null || uri.getScheme == GCS_SCHEME) {
      Right(())
    } else {
      Left(GcsParseError(s"Invalid scheme: ${uri.getScheme}"))
    }
  }

  def getAndValidateHost(path: String, uri: URI): Either[GcsParseError, GcsBucketName] = {
    // Get the host from the URI if we can, and validate it against GCS_BUCKET_NAME_PATTERN
    val parsedFromUri = for {
      h <- Option(uri.getHost)
      _ <- GCS_BUCKET_NAME_PATTERN.findFirstMatchIn(h)
    } yield h

    // It's possible for it to not be a valid URI, but still be a valid bucket name.
    // For example a_bucket_with_underscores is a valid GCS name but not a valid URI.
    // So if URI parsing fails, still try to extract a valid bucket name using GCS_PATH_PATTERN.
    val parsed = parsedFromUri.orElse {
      for {
        m <- GCS_PATH_PATTERN.findFirstMatchIn(path)
        g <- Option(m.group(1))
      } yield g
    }

    parsed.map(GcsBucketName.apply)
      .toRight(GcsParseError(s"Could not parse bucket name from path: $path"))
  }

  def getAndValidateRelativePath(uri: URI): Either[GcsParseError, GcsRelativePath] = {
    Option(uri.getPath).map(GcsRelativePath.apply)
      .toRight(GcsParseError(s"Could not parse bucket relative path from path: ${uri.toString}"))
  }
}