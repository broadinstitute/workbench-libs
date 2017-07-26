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

  val utilDependencies = Seq(
    scalaLogging,
    akkaActor,
    akkaTestkit,
    scalatest,
    mockito
  )
}
