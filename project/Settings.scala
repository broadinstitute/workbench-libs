import Dependencies._
import Merging._
import Testing._
import Version._
import Publishing._
import sbt.Keys.{scalacOptions, _}
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
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"),
    scalacOptions in Test -= "-Ywarn-dead-code" // due to https://github.com/mockito/mockito-scala#notes
  )

  val commonCompilerSettings = Seq(
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "utf-8", // Specify character encoding used by source files.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfatal-warnings", // Fail the compilation if there are any warnings.
    "-Xfuture", // Turn on future language features.
//    "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
    "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match", // Pattern match may not be typesafe.
    "-Ypartial-unification", // Enable partial unification in type constructor inference
//    "-Ywarn-dead-code", // Warn when dead code is identified.
    "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
    "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
//    "-Ywarn-numeric-widen", // Warn when numerics are widened.
    "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
//    "-Ywarn-value-discard", // Warn when non-Unit expression results are unused.
    "-language:higherKinds",
    "-language:postfixOps"
  )

  val commonCrossCompileSettings = Seq(
    crossScalaVersions := List("2.11.8", "2.12.7")
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
    scalaVersion  := "2.12.7",
    resolvers ++= commonResolvers,
    scalacOptions ++= commonCompilerSettings
  )

  val utilSettings = commonSettings ++ List(
    name := "workbench-util",
    libraryDependencies ++= utilDependencies,
    version := createVersion("0.4")
  ) ++ publishSettings

  val modelSettings = commonSettings ++ List(
    name := "workbench-model",
    libraryDependencies ++= modelDependencies,
    version := createVersion("0.12")
  ) ++ publishSettings

  val metricsSettings = commonSettings ++ List(
    name := "workbench-metrics",
    libraryDependencies ++= metricsDependencies,
    version := createVersion("0.4")
  ) ++ publishSettings

  val googleSettings = commonSettings ++ List(
    name := "workbench-google",
    libraryDependencies ++= googleDependencies,
    version := createVersion("0.18"),
    coverageExcludedPackages := ".*HttpGoogle.*DAO.*"
//    ,
//    addCompilerPlugin(
//      ("org.scalameta" % "paradise" % "3.0.0-M11").cross(CrossVersion.full))
  ) ++ publishSettings

  val serviceTestSettings = commonSettings ++ List(
    name := "workbench-service-test",
    libraryDependencies ++= serviceTestDependencies,
    version := createVersion("0.13")
  ) ++ publishSettings

  val notificationsSettings = commonSettings ++ List(
    name := "workbench-notifications",
    libraryDependencies ++= notificationsDependencies,
    version := createVersion("0.2")
  ) ++ publishSettings

  val rootSettings = commonSettings ++ noPublishSettings ++ noTestSettings

}
