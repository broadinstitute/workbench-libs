import sbt._

object Dependencies {
  val akkaV         = "2.5.3"
  val akkaHttpV     = "10.0.6"
  val catsV         = "0.9.0"
  val jacksonV      = "2.8.7"
  val googleV       = "1.22.0"
  val scalaLoggingV = "3.7.2"
  val scalaTestV    = "3.0.1"

  def excludeGuavaJDK5(m: ModuleID): ModuleID = m.exclude("com.google.guava", "guava-jdk5")

  val scalaLogging: ModuleID = "com.typesafe.scala-logging"    %% "scala-logging" % "3.7.2"  % "provided"
  val scalatest: ModuleID =    "org.scalatest"                 %% "scalatest"     % "3.0.1"  % "test"
  val mockito: ModuleID =      "org.mockito"                   %  "mockito-core"  % "2.8.47" % "test"

  val akkaActor: ModuleID =         "com.typesafe.akka" %% "akka-actor"           % akkaV     % "provided"
  val akkaHttp: ModuleID =          "com.typesafe.akka" %% "akka-http"            % akkaHttpV % "provided"
  val akkaHttpSprayJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV % "provided"
  val akkaTestkit: ModuleID =       "com.typesafe.akka" %% "akka-testkit"         % akkaV     % "test"
  val akkaHttpTestkit: ModuleID =   "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpV % "test"

  val cats: ModuleID = "org.typelevel" %% "cats" % catsV

  // metrics-scala transitively pulls in io.dropwizard.metrics:metrics-core
  val metricsScala: ModuleID =      "nl.grons"              %% "metrics-scala"    % "3.5.6"
  val metricsStatsd: ModuleID =     "com.readytalk"         %  "metrics3-statsd"  % "4.2.0"

  val googleApiClient: ModuleID =            "com.google.api-client" % "google-api-client"                        % googleV
  val googleCloudBilling: ModuleID =         "com.google.apis"       % "google-api-services-cloudbilling"         % s"v1-rev13-$googleV"
  val googleGenomics: ModuleID =             "com.google.apis"       % "google-api-services-genomics"             % s"v1-rev504-$googleV"
  val googleStorage: ModuleID =              "com.google.apis"       % "google-api-services-storage"              % s"v1-rev109-$googleV"
  val googleCloudResourceManager: ModuleID = "com.google.apis"       % "google-api-services-cloudresourcemanager" % s"v1-rev446-$googleV"
  val googleCompute: ModuleID =              "com.google.apis"       % "google-api-services-compute"              % s"v1-rev152-$googleV"
  val googleAdminDirectory: ModuleID =       "com.google.apis"       % "google-api-services-admin-directory"      % s"directory_v1-rev82-$googleV"
  val googlePlus: ModuleID =                 "com.google.apis"       % "google-api-services-plus"                 % s"v1-rev529-$googleV"
  val googleOAuth2: ModuleID =               "com.google.apis"       % "google-api-services-oauth2"               % s"v1-rev127-$googleV"
  val googlePubSub: ModuleID =               "com.google.apis"       % "google-api-services-pubsub"               % s"v1-rev357-$googleV"
  val googleServicemanagement: ModuleID =    "com.google.apis"       % "google-api-services-servicemanagement"    % s"v1-rev359-$googleV"
  val googleGuava: ModuleID = "com.google.guava"  % "guava" % "22.0"

  val commonDependencies = Seq(
    scalaLogging,
    scalatest
  )

  val utilDependencies = commonDependencies ++ Seq(
    akkaActor,
    cats,
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

  val googleDependencies = commonDependencies ++ Seq(
    googleCloudBilling,
    googleGenomics,
    googleStorage,
    googleCloudResourceManager,
    googleCompute,
    googleAdminDirectory,
    googlePlus,
    googleOAuth2,
    googlePubSub,
    googleServicemanagement,
    googleGuava,
    akkaHttpSprayJson,
    akkaTestkit
  ).map(excludeGuavaJDK5)
}
