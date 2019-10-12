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
    "artifactory-snapshots" at artifactory + "libs-snapshot",
    Resolver.sonatypeRepo("releases")
  )

  //coreDefaultSettings + defaultConfigs = the now deprecated defaultSettings
  lazy val commonBuildSettings = Defaults.coreDefaultSettings ++ Defaults.defaultConfigs ++ Seq(
    javaOptions += "-Xmx2G",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"),
    scalacOptions in Test -= "-Ywarn-dead-code", // due to https://github.com/mockito/mockito-scala#notes
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
  )

  lazy val commonCompilerSettings = scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n <= 11 => // for 2.11 all we care about is capabilities, not warnings
      Seq(
        "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
        "-language:higherKinds",             // Allow higher-kinded types
        "-language:implicitConversions",     // Allow definition of implicit functions called views
        "-Ypartial-unification"              // Enable partial unification in type constructor inference
      )
    case Some((2, 12)) =>
      Seq(
        "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
        "-encoding", "utf-8",                // Specify character encoding used by source files.
        "-explaintypes",                     // Explain type errors in more detail.
        "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
        "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
        "-language:higherKinds",             // Allow higher-kinded types
        "-language:implicitConversions",     // Allow definition of implicit functions called views
        "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
        "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
        "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
        "-Xfuture",                          // Turn on future language features.
        "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
        "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
        "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
        "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
        "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
        "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
        "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
        "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
        "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
        "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
        "-Xlint:option-implicit",            // Option.apply used implicit view.
        "-Xlint:package-object-classes",     // Class or object defined in package object.
        "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
        "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
        "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
        "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
        "-Xlint:unsound-match",              // Pattern match may not be typesafe.
        "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
        // "-Yno-imports",                      // No predef or default imports
        "-Ypartial-unification",             // Enable partial unification in type constructor inference
        "-Ywarn-dead-code",                  // Warn when dead code is identified.
        "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
        "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
        "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
        "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
        "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
//        "-Ywarn-numeric-widen",              // Warn when numerics are widened.
        "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
        "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
//        "-Ywarn-unused:locals",              // Warn if a local definition is unused.
//        "-Ywarn-unused:params",              // Warn if a value parameter is unused.
        "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
//        "-Ywarn-unused:privates",            // Warn if a private member is unused.
//        "-Ywarn-value-discard",               // Warn when non-Unit expression results are unused.
        "-language:postfixOps"
      )
  })

  val commonCrossCompileSettings = Seq(
    crossScalaVersions := List("2.11.12", "2.12.10")
  )

  val only212 = Seq(
    crossScalaVersions := List("2.12.10")
  )

  //sbt assembly settings
  val commonAssemblySettings = Seq(
    assemblyMergeStrategy in assembly := customMergeStrategy((assemblyMergeStrategy in assembly).value),
    test in assembly := {}
  )

  //common settings for all sbt subprojects
  val commonSettings = commonBuildSettings ++ commonAssemblySettings ++ commonTestSettings ++ List(
    organization  := "org.broadinstitute.dsde.workbench",
    scalaVersion  := "2.12.8",
    resolvers ++= commonResolvers,
    commonCompilerSettings
  )

  val utilSettings = commonCrossCompileSettings ++ commonSettings ++ List(
    name := "workbench-util",
    libraryDependencies ++= utilDependencies,
    version := createVersion("0.5")
  ) ++ publishSettings

  val util2Settings = only212 ++ commonSettings ++ List(
    name := "workbench-util2",
    libraryDependencies ++= util2Dependencies,
    version := createVersion("0.1")
  ) ++ publishSettings

  val modelSettings = commonCrossCompileSettings ++ commonSettings ++ List(
    name := "workbench-model",
    libraryDependencies ++= modelDependencies,
    version := createVersion("0.13")
  ) ++ publishSettings

  val metricsSettings = only212 ++ commonSettings ++ List(
    name := "workbench-metrics",
    libraryDependencies ++= metricsDependencies,
    version := createVersion("0.5")
  ) ++ publishSettings

  val googleSettings = only212 ++ commonSettings ++ List(
    name := "workbench-google",
    libraryDependencies ++= googleDependencies,
    version := createVersion("0.21"),
    coverageExcludedPackages := ".*HttpGoogle.*DAO.*"
  ) ++ publishSettings

  val google2Settings = only212 ++ commonSettings ++ List(
    name := "workbench-google2",
    libraryDependencies ++= google2Dependencies,
    version := createVersion("0.6")
  ) ++ publishSettings

  val newrelicSettings = only212 ++ commonSettings ++ List(
    name := "workbench-newrelic",
    libraryDependencies ++= newrelicDependencies,
    version := createVersion("0.3")
  ) ++ publishSettings

  val serviceTestSettings = only212 ++ commonSettings ++ List(
    name := "workbench-service-test",
    libraryDependencies ++= serviceTestDependencies,
    version := createVersion("0.16")
  ) ++ publishSettings

  val notificationsSettings = only212 ++ commonSettings ++ List(
    name := "workbench-notifications",
    libraryDependencies ++= notificationsDependencies,
    version := createVersion("0.3")
  ) ++ publishSettings

  val rootSettings = commonSettings ++ noPublishSettings ++ noTestSettings

}
