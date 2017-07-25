import sbt.Keys._
import sbt._


object Version {

  def createVersion(baseVersion: String) = {
    def getLastCommitFromGit = { s"""git rev-parse --short HEAD""" !! }

    // either specify git hash as an env var or derive it
    // if building from the broadinstitute/scala-baseimage docker image use env var
    // (scala-baseimage doesn't have git in it)
    val lastCommit = sys.env.getOrElse("GIT_HASH", getLastCommitFromGit ).trim()
    baseVersion + "-" + lastCommit
  }
}
