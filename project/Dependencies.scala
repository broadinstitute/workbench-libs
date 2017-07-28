import sbt._

object Dependencies {
  val akkaV = "2.5.3"
  val akkaHttpV = "10.0.6"
  val jacksonV = "2.8.7"
  val googleV = "1.22.0"
  val olderGoogleV = "1.20.0"

  def excludeGuavaJDK5(m: ModuleID): ModuleID = m.exclude("com.google.guava", "guava-jdk5")

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

  val googleApiClient: ModuleID =             excludeGuavaJDK5("com.google.api-client"  % "google-api-client"                         % googleV)
  val googleCloudBilling: ModuleID =          excludeGuavaJDK5("com.google.apis"        % "google-api-services-cloudbilling"          % ("v1-rev7-" + googleV))
  val googleGenomics: ModuleID =              excludeGuavaJDK5("com.google.apis"        % "google-api-services-genomics"              % ("v1-rev89-" + googleV))
  val googleStorage: ModuleID =               excludeGuavaJDK5("com.google.apis"        % "google-api-services-storage"               % ("v1-rev35-" + olderGoogleV))
  val googleCloudResourceManager: ModuleID =  excludeGuavaJDK5("com.google.apis"        % "google-api-services-cloudresourcemanager"  % ("v1-rev7-" + googleV))

  val googleCompute: ModuleID =           "com.google.apis"   % "google-api-services-compute"           % ("v1-rev72-" + olderGoogleV)
  val googleAdminDirectory: ModuleID =    "com.google.apis"   % "google-api-services-admin-directory"   % ("directory_v1-rev53-" + olderGoogleV)
  val googlePlus: ModuleID =              "com.google.apis"   % "google-api-services-plus"              % ("v1-rev381-" + olderGoogleV)
  val googleOAuth2: ModuleID =            "com.google.apis"   % "google-api-services-oauth2"            % ("v1-rev112-" + googleV)
  val googlePubSub: ModuleID =            "com.google.apis"   % "google-api-services-pubsub"            % ("v1-rev14-" + googleV)
  val googleServicemanagement: ModuleID = "com.google.apis"   % "google-api-services-servicemanagement" % ("v1-rev17-" + googleV)
  val googleGuava: ModuleID =             "com.google.guava"  % "guava" % "19.0"

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
    googleGuava
  )
}
