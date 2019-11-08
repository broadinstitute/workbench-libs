import sbt._

object Dependencies {
  val akkaV         = "2.5.3"
  val akkaHttpV     = "10.0.6"
  val jacksonV      = "2.9.0"
  val googleV       = "1.22.0"
  val scalaLoggingV = "3.7.2"
  val scalaTestV    = "3.0.1"
  val circeVersion = "0.12.2"
  val http4sVersion = "0.21.0-M5"

  def excludeGuavaJDK5(m: ModuleID): ModuleID = m.exclude("com.google.guava", "guava-jdk5")

  val scalaLogging: ModuleID = "com.typesafe.scala-logging"    %% "scala-logging" % "3.9.2"  % "provided"
  val scalatest: ModuleID =    "org.scalatest"                 %% "scalatest"     % "3.0.5"  % "test"
  val mockito: ModuleID =      "org.mockito"                   %  "mockito-core"  % "2.8.47" % "test"

  val akkaActor: ModuleID =         "com.typesafe.akka" %% "akka-actor"           % akkaV     % "provided"
  val akkaHttp: ModuleID =          "com.typesafe.akka" %% "akka-http"            % akkaHttpV % "provided"
  val akkaHttpSprayJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV % "provided"
  val akkaTestkit: ModuleID =       "com.typesafe.akka" %% "akka-testkit"         % akkaV     % "test"
  val akkaHttpTestkit: ModuleID =   "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpV % "test"
  val scalaCheck: ModuleID =        "org.scalacheck"      %%  "scalacheck"        % "1.14.0"  % "test"
  val commonsCodec: ModuleID = "commons-codec" % "commons-codec" % "1.12" % "test"

  val jacksonModule: ModuleID =   "com.fasterxml.jackson.module" %% "jackson-module-scala"   % jacksonV % "test"

  val selenium: ModuleID = "org.seleniumhq.selenium" % "selenium-java" % "3.11.0" % "test"

  val catsEffect: ModuleID = "org.typelevel" %% "cats-effect" % "2.0.0"

  // metrics-scala transitively pulls in io.dropwizard.metrics:metrics-core
  val metricsScala: ModuleID =      "nl.grons"              %% "metrics-scala"    % "3.5.6"
  val metricsStatsd: ModuleID =     "com.readytalk"         %  "metrics3-statsd"  % "4.2.0"

  val googleApiClient: ModuleID =            "com.google.api-client" % "google-api-client"                        % googleV
  val googleCloudBilling: ModuleID =         "com.google.apis"       % "google-api-services-cloudbilling"         % s"v1-rev21-$googleV"
  val googleGenomics: ModuleID =             "com.google.apis"       % "google-api-services-genomics"             % s"v1-rev504-$googleV"
  val googleStorage: ModuleID =              "com.google.apis"       % "google-api-services-storage"              % s"v1-rev109-$googleV"
  val googleCloudResourceManager: ModuleID = "com.google.apis"       % "google-api-services-cloudresourcemanager" % s"v1-rev446-$googleV"
  val googleCompute: ModuleID =              "com.google.apis"       % "google-api-services-compute"              % s"v1-rev152-$googleV"
  val googleAdminDirectory: ModuleID =       "com.google.apis"       % "google-api-services-admin-directory"      % s"directory_v1-rev82-$googleV"
  val googleGroupsSettings: ModuleID =       "com.google.apis"       % "google-api-services-groupssettings"       % s"v1-rev74-$googleV"
  val googlePlus: ModuleID =                 "com.google.apis"       % "google-api-services-plus"                 % s"v1-rev529-$googleV"
  val googleOAuth2: ModuleID =               "com.google.apis"       % "google-api-services-oauth2"               % s"v1-rev127-$googleV"
  val googlePubSub: ModuleID =               "com.google.apis"       % "google-api-services-pubsub"               % s"v1-rev357-$googleV"
  val googleServicemanagement: ModuleID =    "com.google.apis"       % "google-api-services-servicemanagement"    % s"v1-rev359-$googleV"
  val googleIam: ModuleID =                  "com.google.apis"       % "google-api-services-iam"                  % s"v1-rev215-$googleV"
  val googleBigQuery: ModuleID =             "com.google.apis"       % "google-api-services-bigquery"             % s"v2-rev377-$googleV"
  val googleGuava: ModuleID = "com.google.guava"  % "guava" % "22.0"
  val googleRpc: ModuleID =               "io.grpc" % "grpc-core" % "1.16.1" //old google libraries relies on older version of grpc

  val googleRpc2: ModuleID =               "io.grpc" % "grpc-core" % "1.24.0" //google2 may depends on newer version of grpc
  val googleFirestore: ModuleID = "com.google.cloud" % "google-cloud-firestore" % "0.71.0-beta"
  val googleStorageNew: ModuleID = "com.google.cloud" % "google-cloud-storage" % "1.100.0"
  val googleStorageLocal: ModuleID = "com.google.cloud" % "google-cloud-nio" % "0.111.0-alpha" % "test"
  val googlePubsubNew: ModuleID = "com.google.cloud" % "google-cloud-pubsub" % "1.62.0"
  val googleKms: ModuleID = "com.google.cloud" % "google-cloud-kms" % "0.77.0-beta"
  val googleDataproc: ModuleID =    "com.google.cloud" % "google-cloud-dataproc" % "0.111.0"

  val circeCore: ModuleID = "io.circe" %% "circe-core" % circeVersion
  val circeParser: ModuleID = "io.circe" %% "circe-parser" % circeVersion
  val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % circeVersion % "test"
  val circeFs2: ModuleID = "io.circe" %% "circe-fs2" % "0.12.0"
  val cats: ModuleID = "org.typelevel" %% "cats-core" % "2.0.0"
  val log4cats = "io.chrisdavenport" %% "log4cats-slf4j"   % "1.0.0"
  val catsMtl = "org.typelevel" %% "cats-mtl-core" % "0.7.0"

  val http4sCirce = "org.http4s" %% "http4s-circe" % http4sVersion
  val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % http4sVersion
  val http4sDsl = "org.http4s"      %% "http4s-dsl"          % http4sVersion

  val fs2Io: ModuleID = "co.fs2" %% "fs2-io" % "2.0.1"
  val rawlsModel: ModuleID = "org.broadinstitute.dsde" %% "rawls-model" % "0.1-0d02c8ce-SNAP" exclude("com.typesafe.scala-logging", "scala-logging_2.11") exclude("com.typesafe.akka", "akka-stream_2.11")
  val newRelic: ModuleID = "com.newrelic.agent.java" % "newrelic-api" % "5.0.0"
  val sealerate: ModuleID = "ca.mrvisser" %% "sealerate" % "0.0.5"

  val silencerVersion = "1.4.1"
  val silencer: ModuleID = compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion)
  val silencerLib: ModuleID = "com.github.ghik" %% "silencer-lib" % silencerVersion % Provided

  val commonDependencies = Seq(
    scalatest,
    scalaCheck
  )

  val utilDependencies = commonDependencies ++ Seq(
    scalaLogging,
    akkaActor,
    akkaHttpSprayJson,
    akkaTestkit,
    mockito,
    cats
  )

  val modelDependencies = commonDependencies ++ Seq(
    scalaLogging,
    akkaHttpSprayJson,
    googleGuava,
    commonsCodec
  )

  val metricsDependencies = commonDependencies ++ Seq(
    scalaLogging,
    metricsScala,
    metricsStatsd,
    akkaHttp,
    akkaTestkit,
    akkaHttpTestkit,
    mockito
  )

  val googleDependencies = commonDependencies ++ Seq(
    scalaLogging,
    googleCloudBilling,
    googleGenomics,
    googleStorage,
    googleCloudResourceManager,
    googleCompute,
    googleAdminDirectory,
    googleGroupsSettings,
    googlePlus,
    googleOAuth2,
    googlePubSub,
    googleServicemanagement,
    googleIam,
    googleBigQuery,
    googleGuava,
    googleRpc,
    googleKms,
    akkaHttpSprayJson,
    akkaTestkit,
    sealerate,
    silencer,
    silencerLib
  ).map(excludeGuavaJDK5)

  val google2Dependencies = commonDependencies ++ Seq(
    googleRpc,
    googleFirestore,
    googleStorageNew,
    googleStorageLocal,
    googlePubsubNew,
    googleKms,
    googleDataproc,
    http4sCirce,
    http4sBlazeClient,
    http4sDsl,
    log4cats,
    circeFs2,
    catsMtl
  )

  val newrelicDependencies = Seq(
    catsEffect,
    log4cats,
    newRelic
  )

  val util2Dependencies = commonDependencies ++ List(
    catsEffect,
    log4cats,
    fs2Io,
    circeFs2,
    circeCore,
    circeParser,
    circeGeneric
  )

  val serviceTestDependencies = commonDependencies ++ Seq(
    scalaLogging,
    akkaActor,
    akkaHttp,
    akkaHttpSprayJson,
    akkaTestkit,
    jacksonModule,
    rawlsModel,
    selenium
  )

  val notificationsDependencies = commonDependencies ++ Seq(
    scalaLogging,
    akkaHttpSprayJson
  )

  val uiTestDependencies = commonDependencies
}
