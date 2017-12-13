import Dependencies._
import Merging._
import Testing._
import Version._
import Publishing._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import scoverage.ScoverageKeys.coverageExcludedPackages

object Settings {

  val artifactory = "https://broadinstitute.jfrog.io/broadinstitute/"

  val commonResolvers = List(
    "artifactory-releases" at artifactory + "libs-release",
    "artifactory-snapshots" at artifactory + "libs-snapshot"
  )

  //coreDefaultSettings + defaultConfigs = the now deprecated defaultSettings
  val commonBuildSettings = Defaults.coreDefaultSettings ++ Defaults.defaultConfigs ++ Seq(
    javaOptions += "-Xmx2G",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
  )

  val commonCompilerSettings = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-encoding", "utf8",
    "-target:jvm-1.8",
    "-language:postfixOps"
  )

  val commonCrossCompileSettings = Seq(
    crossScalaVersions := List("2.11.8", "2.12.3")
  )

  //sbt assembly settings
  val commonAssemblySettings = Seq(
    assemblyMergeStrategy in assembly := customMergeStrategy((assemblyMergeStrategy in assembly).value),
    test in assembly := {}
  )

  //common settings for all sbt subprojects
  val commonSettings =
    commonBuildSettings ++ commonAssemblySettings ++ commonTestSettings ++ commonCrossCompileSettings ++ List(
    organization  := "org.broadinstitute.dsde.workbench",
    scalaVersion  := "2.12.3",
    resolvers ++= commonResolvers,
    scalacOptions ++= commonCompilerSettings
  )

  val utilSettings = commonSettings ++ List(
    name := "workbench-util",
    libraryDependencies ++= utilDependencies,
    version := createVersion("0.2")
  ) ++ publishSettings

  val modelSettings = commonSettings ++ List(
    name := "workbench-model",
    libraryDependencies ++= modelDependencies,
    version := createVersion("0.9")
  ) ++ publishSettings

  val metricsSettings = commonSettings ++ List(
    name := "workbench-metrics",
    libraryDependencies ++= metricsDependencies,
    version := createVersion("0.3")
  ) ++ publishSettings

  val googleSettings = commonSettings ++ List(
    name := "workbench-google",
    libraryDependencies ++= googleDependencies,
    version := createVersion("0.10"),
    coverageExcludedPackages := ".*HttpGoogle.*DAO.*"
  ) ++ publishSettings

  val serviceTestSettings = commonSettings ++ List(
    name := "workbench-service-test",
    libraryDependencies ++= serviceTestDependencies,
    version := createVersion("0.1")
  ) ++ publishSettings

  val uiTestSettings = commonSettings ++ List(
    name := "workbench-ui-test",
    libraryDependencies ++= uiTestDependencies,
    version := createVersion("0.1")
  ) ++ publishSettings

  val rootSettings = commonSettings ++ noPublishSettings ++ noTestSettings

}
