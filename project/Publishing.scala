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
    Seq(
      publishTo := Some(garResolver(sys.props.getOrElse("project.isSnapshot", "false").toBoolean)),
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
