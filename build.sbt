import Settings._
import Testing._

val testAndCompile = "test->test;compile->compile"

lazy val workbenchUtil = project.in(file("util"))
  .settings(utilSettings:_*)
  .dependsOn(workbenchModel)
  .withTestSettings

lazy val workbenchModel = project.in(file("model"))
  .settings(modelSettings:_*)
  .withTestSettings

lazy val workbenchMetrics = project.in(file("metrics"))
  .settings(metricsSettings:_*)
  .dependsOn(workbenchUtil % testAndCompile)
  .withTestSettings

lazy val workbenchGoogle = project.in(file("google"))
  .settings(googleSettings:_*)
  .dependsOn(workbenchUtil % testAndCompile)
  .dependsOn(workbenchModel)
  .dependsOn(workbenchMetrics % testAndCompile)
  .withTestSettings

lazy val workbenchGoogle2 = project.in(file("google2"))
  .settings(google2Settings:_*)
  .dependsOn(workbenchUtil % testAndCompile)
  .dependsOn(workbenchModel)
  .withTestSettings

lazy val workbenchServiceTest = project.in(file("serviceTest"))
  .settings(serviceTestSettings:_*)
  .dependsOn(workbenchModel % testAndCompile)
  .dependsOn(workbenchGoogle)
  .withTestSettings

lazy val workbenchNotifications = project.in(file("notifications"))
  .settings(notificationsSettings:_*)
  .dependsOn(workbenchModel % testAndCompile)
  .dependsOn(workbenchGoogle)
  .withTestSettings

/*
lazy val workbenchUiTest = project.in(file("uiTest"))
  .settings(uiTestSettings)
  .withTestSettings
*/

lazy val workbenchLibs = project.in(file("."))
  .settings(rootSettings:_*)
  .aggregate(workbenchUtil)
  .aggregate(workbenchModel)
  .aggregate(workbenchMetrics)
  .aggregate(workbenchGoogle)
  .aggregate(workbenchGoogle2)
  .aggregate(workbenchServiceTest)
  .aggregate(workbenchNotifications)
  .settings(crossScalaVersions := List())

Revolver.settings

Revolver.enableDebugging(port = 5051, suspend = false)

// When JAVA_OPTS are specified in the environment, they are usually meant for the application
// itself rather than sbt, but they are not passed by default to the application, which is a forked
// process. This passes them through to the "re-start" command, which is probably what a developer
// would normally expect.
// for some reason using ++= causes revolver not to find the main class so do the stupid map below
//javaOptions in reStart ++= sys.env.getOrElse("JAVA_OPTS", "").split(" ").toSeq
sys.env.getOrElse("JAVA_OPTS", "").split(" ").toSeq.map { opt =>
  javaOptions in reStart += opt
}

bloopAggregateSourceDependencies in Global := true