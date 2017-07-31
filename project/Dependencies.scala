import sbt._

object Dependencies {
  val akkaV = "2.5.3"
  val akkaHttpV = "10.0.6"
  val jacksonV = "2.8.7"

  val scalaLogging: ModuleID = "com.typesafe.scala-logging"    %% "scala-logging"        % "3.7.2"
  val akkaActor: ModuleID =   "com.typesafe.akka" %% "akka-actor"   % akkaV
  val akkaTestkit: ModuleID = "com.typesafe.akka" %% "akka-testkit" % akkaV % "test"
  val scalatest: ModuleID =       "org.scalatest"                 %% "scalatest"            % "3.0.1" % "test"
  val mockito: ModuleID =         "org.mockito"                   % "mockito-core"          % "2.8.47" % "test"

  val akkaHttp: ModuleID = "com.typesafe.akka"   %%  "akka-http" % akkaHttpV
  val akkaHttpSprayJson: ModuleID = "com.typesafe.akka"   %%  "akka-http-spray-json" % akkaHttpV
  val akkaHttpTestkit: ModuleID = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV

  // metrics-scala transitively pulls in io.dropwizard.metrics:metrics-core
  val metricsScala: ModuleID =       "nl.grons"              %% "metrics-scala"    % "3.5.6"
  val metricsStatsd: ModuleID =      "com.readytalk"         %  "metrics3-statsd"  % "4.2.0"

  val commonDependencies = Seq(
    scalaLogging,
    scalatest
  )

  val utilDependencies = commonDependencies ++ Seq(
    akkaActor,
    akkaTestkit,
    mockito
  )

  val modelDependencies = commonDependencies ++ Seq(
    akkaHttpSprayJson
  )

  val metricsDependencies = commonDependencies ++ Seq(
    metricsScala,
    metricsStatsd,
    akkaHttp,
    akkaTestkit,
    akkaHttpTestkit,
    mockito
  )
}
