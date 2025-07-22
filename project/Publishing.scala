import sbt.Keys._
import sbt._

/**
 * NOTE: This was lifted wholesale from Cromwell.
 */

object Publishing {
  private val buildTimestamp = System.currentTimeMillis() / 1000

  private val garBase = "artifactregistry://us-central1-maven.pkg.dev/dsp-artifact-registry/"

  private def garResolver(isSnapshot: Boolean): Resolver = {
    val repoType = if (isSnapshot) "snapshot" else "release"
    val repoUrl = s"${garBase}libs-$repoType-standard"
    val repoName = "gar-publish"
    repoName at repoUrl
  }

  val publishSettings: Seq[Setting[_]] =
    // we only publish to libs-release-local because of a bug in sbt that makes snapshots take
    // priority over the local package cache. see here: https://github.com/sbt/sbt/issues/2687#issuecomment-236586241
    Seq(
      publishTo := Some(garResolver(false)),
      Compile / publishArtifact := true,
      Test / publishArtifact := true,
      Compile / packageDoc / publishArtifact := false
    )

  val noPublishSettings: Seq[Setting[_]] =
    Seq(
      publish := {},
      publishLocal := {}
    )
}
