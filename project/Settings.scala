import Dependencies._
import Testing._
import Version._
import Publishing._
import sbt.Keys.{scalacOptions, _}
import sbt._
import scoverage.ScoverageKeys.coverageExcludedPackages

//noinspection TypeAnnotation
object Settings {

  val artifactory = "https://broadinstitute.jfrog.io/broadinstitute/"

  val commonResolvers = List(
    "artifactory-releases" at artifactory + "libs-release",
    "artifactory-snapshots" at artifactory + "libs-snapshot",
    Resolver.sonatypeRepo("releases")
  )

  // coreDefaultSettings + defaultConfigs = the now deprecated defaultSettings
  lazy val commonBuildSettings = Defaults.coreDefaultSettings ++ Defaults.defaultConfigs ++ Seq(
    javaOptions += "-Xmx2G",
    javacOptions ++= Seq("--release", "11", "-target:jvm-11"),
    Compile / console / scalacOptions --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"),
    Test / console / scalacOptions --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"),
    Test / scalacOptions -= "-Ywarn-dead-code" // due to https://github.com/mockito/mockito-scala#notes
  )

  lazy val commonCompilerSettings = scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) =>
      Seq(
        "-deprecation", // Emit warning and location for usages of deprecated APIs.
        "-encoding",
        "utf-8", // Specify character encoding used by source files.
        "-explaintypes", // Explain type errors in more detail.
        "-feature", // Emit warning and location for usages of features that should be imported explicitly.
        "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
        "-language:higherKinds", // Allow higher-kinded types
        "-language:implicitConversions", // Allow definition of implicit functions called views
        "-unchecked", // Enable additional warnings where generated code depends on assumptions.
//        "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
//        "-Xfatal-warnings", // Fail the compilation if there are any warnings.
        "-Xfuture", // Turn on future language features.
        "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
        "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
        "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
        "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
        "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
        "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
        "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
        "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
        "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
        "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
        "-Xlint:option-implicit", // Option.apply used implicit view.
        "-Xlint:package-object-classes", // Class or object defined in package object.
        "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
        "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
        "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
        "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
        "-Xlint:unsound-match", // Pattern match may not be typesafe.
        "-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
        "-Ypartial-unification", // Enable partial unification in type constructor inference
        "-Ywarn-dead-code", // Warn when dead code is identified.
        "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
        "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
        "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
        "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
        "-Ywarn-nullary-unit", // Warn when nullary methods return Unit.
        "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
        "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
        "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
        "-language:postfixOps"
      )
    case Some((2, 13)) =>
      Seq(
//      "-deprecation", // Emit warning and location for usages of deprecated APIs. TODO: enable this when we migrate off of 2.12
        "-explaintypes", // Explain type errors in more detail.
        "-feature", // Emit warning and location for usages of features that should be imported explicitly.
        "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
        "-language:experimental.macros", // Allow macro definition (besides implementation and application)
        "-language:higherKinds", // Allow higher-kinded types
        "-language:implicitConversions", // Allow definition of implicit functions called views
        "-unchecked", // Enable additional warnings where generated code depends on assumptions.
//        "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
//      "-Xfatal-warnings", // Fail the compilation if there are any warnings.
        "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
        "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
        "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
        "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
        "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
        "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
        "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
        "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
        "-Xlint:option-implicit", // Option.apply used implicit view.
        "-Xlint:package-object-classes", // Class or object defined in package object.
        "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
        "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
        "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
        "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
        "-Ywarn-dead-code", // Warn when dead code is identified.
        "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
        "-Ywarn-unused:locals", // Warn if a local definition is unused.
        "-Ywarn-unused:params", // Warn if a value parameter is unused.
        "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
        "-Ywarn-unused:privates", // Warn if a private member is unused.
        "-Ybackend-parallelism",
        "8", // Enable paralellisation — change to desired number!
        "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
        "-Ycache-macro-class-loader:last-modified", // and macro definitions. This can lead to performance improvements.
        "-language:postfixOps"
      )
  })

  val cross212and213 = Seq(
    crossScalaVersions := List("2.12.12", "2.13.7")
  )

  // common settings for all sbt subprojects
  val commonSettings = commonBuildSettings ++ commonTestSettings ++ List(
    organization := "org.broadinstitute.dsde.workbench",
    scalaVersion := "2.13.7",
    resolvers ++= commonResolvers,
    commonCompilerSettings
  )

  val utilSettings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-util",
    libraryDependencies ++= utilDependencies,
    version := createVersion("0.6")
  ) ++ publishSettings

  val util2Settings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-util2",
    libraryDependencies ++= util2Dependencies,
    version := createVersion("0.2")
  ) ++ publishSettings

  val modelSettings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-model",
    libraryDependencies ++= modelDependencies,
    version := createVersion("0.15")
  ) ++ publishSettings

  val metricsSettings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-metrics",
    libraryDependencies ++= metricsDependencies,
    version := createVersion("0.5")
  ) ++ publishSettings

  val googleSettings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-google",
    libraryDependencies ++= googleDependencies,
    version := createVersion("0.21"),
    coverageExcludedPackages := ".*HttpGoogle.*DAO.*"
  ) ++ publishSettings

  val google2Settings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-google2",
    libraryDependencies ++= google2Dependencies,
    version := createVersion("0.23")
  ) ++ publishSettings

  val openTelemetrySettings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-openTelemetry",
    libraryDependencies ++= openTelemetryDependencies,
    version := createVersion("0.2")
  ) ++ publishSettings

  val errorReportingSettings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-error-reporting",
    libraryDependencies ++= errorReportingDependencies,
    version := createVersion("0.2")
  ) ++ publishSettings

  val serviceTestSettings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-service-test",
    libraryDependencies ++= serviceTestDependencies,
    version := createVersion("0.21")
  ) ++ publishSettings

  val notificationsSettings = cross212and213 ++ commonSettings ++ List(
    name := "workbench-notifications",
    libraryDependencies ++= notificationsDependencies,
    version := createVersion("0.3")
  ) ++ publishSettings

  val rootSettings = commonSettings ++ noPublishSettings ++ noTestSettings

}
