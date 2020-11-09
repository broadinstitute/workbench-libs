import Settings._
import Testing._

val testAndCompile = "test->test;compile->compile"

lazy val workbenchUtil = project
  .in(file("util"))
  .settings(utilSettings: _*)
  .dependsOn(workbenchModel)
  .withTestSettings

lazy val workbenchUtil2 = project
  .in(file("util2"))
  .settings(util2Settings: _*)
  .withTestSettings

lazy val workbenchModel = project
  .in(file("model"))
  .settings(modelSettings: _*)
  .withTestSettings

lazy val workbenchMetrics = project
  .in(file("metrics"))
  .settings(metricsSettings: _*)
  .dependsOn(workbenchUtil % testAndCompile)
  .withTestSettings

lazy val workbenchGoogle = project
  .in(file("google"))
  .settings(googleSettings: _*)
  .dependsOn(workbenchUtil % testAndCompile)
  .dependsOn(workbenchUtil2 % testAndCompile)
  .dependsOn(workbenchModel)
  .dependsOn(workbenchMetrics % testAndCompile)
  .withTestSettings

lazy val workbenchGoogle2 = project
  .in(file("google2"))
  .settings(google2Settings: _*)
  .dependsOn(workbenchUtil2 % testAndCompile)
  .dependsOn(workbenchModel)
  .withTestSettings

lazy val workbenchOpenTelemetry = project
  .in(file("openTelemetry"))
  .settings(openTelemetrySettings: _*)
  .dependsOn(workbenchUtil2 % testAndCompile)
  .withTestSettings

lazy val workbenchErrorReporting = project
  .in(file("errorReporting"))
  .settings(errorReportingSettings: _*)
  .dependsOn(workbenchUtil2 % testAndCompile)
  .withTestSettings

lazy val workbenchServiceTest = project
  .in(file("serviceTest"))
  .settings(serviceTestSettings: _*)
  .dependsOn(workbenchModel % testAndCompile)
  .dependsOn(workbenchGoogle)
  .withTestSettings

lazy val workbenchNotifications = project
  .in(file("notifications"))
  .settings(notificationsSettings: _*)
  .dependsOn(workbenchModel % testAndCompile)
  .dependsOn(workbenchGoogle)
  .withTestSettings

/*
lazy val workbenchUiTest = project.in(file("uiTest"))
  .settings(uiTestSettings)
  .withTestSettings
 */

lazy val workbenchLibs = project
  .in(file("."))
  .settings(rootSettings: _*)
  .aggregate(workbenchUtil)
  .aggregate(workbenchUtil2)
  .aggregate(workbenchModel)
  .aggregate(workbenchMetrics)
  .aggregate(workbenchOpenTelemetry)
  .aggregate(workbenchErrorReporting)
  .aggregate(workbenchGoogle)
  .aggregate(workbenchGoogle2)
  .aggregate(workbenchServiceTest)
  .aggregate(workbenchNotifications)
