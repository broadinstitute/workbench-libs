import sbt._

object Dependencies {
  val akkaV         = "2.6.14"
  val akkaHttpV     = "10.2.4"
  val jacksonV      = "2.12.1"
  val googleV       = "1.22.0"
  val scalaLoggingV = "3.9.4"
  val scalaTestV    = "3.2.9"
  val circeVersion = "0.13.0"
  val http4sVersion = "0.21.23"
  val bouncyCastleVersion = "1.68"

  def excludeGuavaJDK5(m: ModuleID): ModuleID = m.exclude("com.google.guava", "guava-jdk5")

  val logstashLogback: ModuleID = "net.logstash.logback"      % "logstash-logback-encoder" % "6.6" % "provided"
  val scalaLogging: ModuleID = "com.typesafe.scala-logging"    %% "scala-logging" % scalaLoggingV  % "provided"
  val scalatest: ModuleID =    "org.scalatest"                 %% "scalatest"     % scalaTestV  % "test"
  val scalaTestScalaCheck = "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % Test //Since scalatest 3.1.0, scalacheck support is moved to `scalatestplus`
  val scalaTestMockito = "org.scalatestplus" %% "mockito-3-4" % "3.2.9.0" % Test //Since scalatest 3.1.0, mockito support is moved to `scalatestplus`
  val scalaTestSelenium =  "org.scalatestplus" %% "selenium-3-141" % "3.2.9.0" % Test //Since scalatest 3.1.0, selenium support is moved to `scalatestplus`

  val akkaActor: ModuleID =         "com.typesafe.akka" %% "akka-actor"           % akkaV     % "provided"
  val akkaStream: ModuleID =         "com.typesafe.akka" %% "akka-stream"           % akkaV     % "provided"
  val akkaHttp: ModuleID =          "com.typesafe.akka" %% "akka-http"            % akkaHttpV % "provided"
  val akkaHttpSprayJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV % "provided"
  val akkaTestkit: ModuleID =       "com.typesafe.akka" %% "akka-testkit"         % akkaV     % "test"
  val akkaHttpTestkit: ModuleID =   "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpV % "test"
  val scalaCheck: ModuleID =        "org.scalacheck"      %%  "scalacheck"        % "1.15.4"  % "test"
  val commonsCodec: ModuleID = "commons-codec" % "commons-codec" % "20041127.091804" % "test"

  val jacksonModule: ModuleID =   "com.fasterxml.jackson.module" %% "jackson-module-scala"   % jacksonV % "test"

  val bouncyCastle: ModuleID = "org.bouncycastle" % "bcpkix-jdk15on" % bouncyCastleVersion
  val bouncyCastleProviderExt: ModuleID = "org.bouncycastle" % "bcprov-ext-jdk15on" % bouncyCastleVersion
  val bouncyCastleProvider: ModuleID = "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleVersion

  val catsEffect: ModuleID = "org.typelevel" %% "cats-effect" % "2.5.1"

  // metrics-scala transitively pulls in io.dropwizard.metrics:metrics-core
  val metricsScala: ModuleID =      "nl.grons"              %% "metrics4-scala"    % "4.1.19"
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
  val googleGuava: ModuleID = "com.google.guava"  % "guava" % "30.1.1-jre"
  val googleRpc: ModuleID =               "io.grpc" % "grpc-core" % "1.34.0"

  val googleRpc2: ModuleID =               "io.grpc" % "grpc-core" % "1.34.0"
  val googleFirestore: ModuleID = "com.google.cloud" % "google-cloud-firestore" % "2.5.1"
  val googleStorageNew: ModuleID = "com.google.cloud" % "google-cloud-storage" % "1.113.16"
  val googleStorageLocal: ModuleID = "com.google.cloud" % "google-cloud-nio" % "0.122.14" % "test"
  val googlePubsubNew: ModuleID = "com.google.cloud" % "google-cloud-pubsub" % "1.113.4"
  val googleKms: ModuleID = "com.google.cloud" % "google-cloud-kms" % "1.42.3"
  val googleDataproc: ModuleID =    "com.google.cloud" % "google-cloud-dataproc" % "1.5.2"
  val googleComputeNew: ModuleID = "com.google.cloud" % "google-cloud-compute" % "0.119.11-alpha"
  val googleContainer: ModuleID = "com.google.cloud" % "google-cloud-container" % "1.4.0"
  val kubernetesClient: ModuleID = "io.kubernetes" % "client-java" % "11.0.1"
  val googleBigQueryNew: ModuleID = "com.google.cloud" % "google-cloud-bigquery" % "1.134.0"
  val google2CloudBilling = "com.google.cloud" % "google-cloud-billing" % "1.1.18"
  val googleResourceManager =  "com.google.cloud" % "google-cloud-resourcemanager" % "0.118.12-alpha"
  //the below v1 module is a dependency for v2 because it contains the OAuth scopes necessary to created scoped credentials
  val googleContainerV1: ModuleID = "com.google.apis" % "google-api-services-container" % "v1-rev20210607-1.31.5"


  val circeCore: ModuleID = "io.circe" %% "circe-core" % circeVersion
  val circeParser: ModuleID = "io.circe" %% "circe-parser" % circeVersion
  val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % circeVersion % "test"
  val circeFs2: ModuleID = "io.circe" %% "circe-fs2" % circeVersion
  val log4cats = "org.typelevel" %% "log4cats-slf4j"   % "1.2.2"
  val catsMtl = "org.typelevel" %% "cats-mtl" % "1.2.1"

  val http4sCirce = "org.http4s" %% "http4s-circe" % http4sVersion
  val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % http4sVersion
  val http4sDsl = "org.http4s"      %% "http4s-dsl"          % http4sVersion

  val fs2Io: ModuleID = "co.fs2" %% "fs2-io" % "2.5.6"
  val rawlsModel: ModuleID = "org.broadinstitute.dsde" %% "rawls-model" % "0.1-2356e282" exclude("com.typesafe.scala-logging", "scala-logging_2.11") exclude("com.typesafe.akka", "akka-stream_2.11")
  val openCensusApi: ModuleID = "io.opencensus" % "opencensus-api" % "0.28.3"
  val openCensusImpl: ModuleID = "io.opencensus" % "opencensus-impl" % "0.28.3"
  val openCensusStatsStackDriver: ModuleID = "io.opencensus" % "opencensus-exporter-stats-stackdriver" % "0.28.3"
  val openCensusTraceStackDriver: ModuleID = "io.opencensus" % "opencensus-exporter-trace-stackdriver" % "0.28.3"
  val openCensusTraceLogging: ModuleID = "io.opencensus" % "opencensus-exporter-trace-logging" % "0.26.0"
  val sealerate: ModuleID = "ca.mrvisser" %% "sealerate" % "0.0.6"

  val commonDependencies = Seq(
    scalatest,
    scalaCheck,
    scalaTestScalaCheck
  )

  val utilDependencies = commonDependencies ++ Seq(
    scalaLogging,
    akkaActor,
    akkaHttpSprayJson,
    akkaTestkit,
    scalaTestMockito,
    "org.typelevel" %% "cats-core" % "2.6.1"
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
    akkaStream,
    akkaTestkit,
    akkaHttpTestkit,
    scalaTestMockito
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
    akkaActor,
    akkaStream,
    akkaHttpSprayJson,
    akkaTestkit,
    sealerate,
    logstashLogback
  ).map(excludeGuavaJDK5)

  val google2Dependencies = commonDependencies ++ Seq(
    bouncyCastle,
    bouncyCastleProviderExt,
    bouncyCastleProvider,
    googleRpc2,
    googleFirestore,
    googleStorageNew,
    googleStorageLocal,
    googlePubsubNew,
    googleKms,
    googleDataproc,
    googleComputeNew,
    googleBigQueryNew,
    http4sCirce,
    http4sBlazeClient,
    http4sDsl,
    log4cats,
    circeFs2,
    catsMtl,
    googleContainer,
    kubernetesClient,
    googleContainerV1,
    sealerate,
    google2CloudBilling,
    googleResourceManager
  )

  val openTelemetryDependencies = List(
    catsEffect,
    log4cats,
    openCensusApi,
    openCensusImpl,
    openCensusStatsStackDriver,
    openCensusTraceStackDriver
  )

  val errorReportingDependencies = List(
    catsEffect,
    "com.google.cloud" % "google-cloud-errorreporting" % "0.120.42-beta"
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
    akkaStream,
    rawlsModel,
    scalaTestSelenium
  )

  val notificationsDependencies = commonDependencies ++ Seq(
    scalaLogging,
    akkaHttpSprayJson
  )

  val uiTestDependencies = commonDependencies
}
