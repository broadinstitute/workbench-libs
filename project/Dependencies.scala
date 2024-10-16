import sbt._

object Dependencies {
  val akkaV         = "2.6.20"
  val akkaHttpV     = "10.2.10"
  val jacksonV      = "2.17.1"
  val googleV       = "2.0.0"
  val scalaLoggingV = "3.9.5"
  val scalaTestV    = "3.2.17"

  // TODO upgrade to stable 14.x or 15.0 once that includes a fix to https://github.com/circe/circe-yaml/issues/356
  val circeVersion = "0.15.0-M1"
  val http4sVersion = "1.0.0-M38"
  val bouncyCastleVersion = "1.78.1"
  val openCensusV = "0.31.1"

  // avoid expoit https://nvd.nist.gov/vuln/detail/CVE-2023-1370 (see [IA-4176])
  val jsonSmartV = "2.5.0"

  def excludeGuavaJDK5(m: ModuleID): ModuleID = m.exclude("com.google.guava", "guava-jdk5")

  val jose4j: ModuleID =  "org.bitbucket.b_c" % "jose4j" % "0.9.4"
  val logstashLogback: ModuleID = "net.logstash.logback"      % "logstash-logback-encoder" % "7.4" % "provided"
  val scalaLogging: ModuleID = "com.typesafe.scala-logging"    %% "scala-logging" % scalaLoggingV  % "provided"
  val scalatest: ModuleID =    "org.scalatest"                 %% "scalatest"     % scalaTestV  % "test"
  val scalaTestScalaCheck = "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test //Since scalatest 3.1.0, scalacheck support is moved to `scalatestplus`
  val scalaTestMockito = "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test //Since scalatest 3.1.0, mockito support is moved to `scalatestplus`
  val scalaTestSelenium =  "org.scalatestplus" %% "selenium-4-1" % "3.2.12.1" % Test //Since scalatest 3.1.0, selenium support is moved to `scalatestplus`
  val awaitility = "org.awaitility" % "awaitility-scala" % "4.2.0" % Test
  val byteBuddy = "net.bytebuddy" % "byte-buddy" % "1.14.2" % Test

  val akkaActor: ModuleID =         "com.typesafe.akka" %% "akka-actor"           % akkaV     % "provided"
  val akkaStream: ModuleID =         "com.typesafe.akka" %% "akka-stream"           % akkaV     % "provided"
  val akkaHttp: ModuleID =          "com.typesafe.akka" %% "akka-http"            % akkaHttpV % "provided"
  val akkaHttpSprayJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV % "provided"
  val akkaTestkit: ModuleID =       "com.typesafe.akka" %% "akka-testkit"         % akkaV     % "test"
  val akkaHttpTestkit: ModuleID =   "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpV % "test"
  val akkaStreamTestkit: ModuleID = "com.typesafe.akka" %% "akka-stream-testkit"  % akkaV % "test"
  val scalaCheck: ModuleID =        "org.scalacheck"      %%  "scalacheck"        % "1.17.0"  % "test"
  val commonsCodec: ModuleID = "commons-codec" % "commons-codec" % "20041127.091804" % "test"

  val jacksonModule: ModuleID =   "com.fasterxml.jackson.module" %% "jackson-module-scala"   % jacksonV % "test"

  val bouncyCastle: ModuleID = "org.bouncycastle" % "bcpkix-jdk18on" % bouncyCastleVersion
  val bouncyCastleProviderExt: ModuleID = "org.bouncycastle" % "bcprov-ext-jdk18on" % bouncyCastleVersion
  val bouncyCastleProvider: ModuleID = "org.bouncycastle" % "bcprov-jdk18on" % bouncyCastleVersion

  val catsEffect: ModuleID = "org.typelevel" %% "cats-effect" % "3.4.10"

  // metrics-scala transitively pulls in io.dropwizard.metrics:metrics-core
  val metricsScala: ModuleID =      "nl.grons"              %% "metrics4-scala"    % "4.2.9"
  val metricsStatsd: ModuleID =     "com.readytalk"         %  "metrics3-statsd"  % "4.2.0"

  val googleCloudBilling: ModuleID =         "com.google.apis"       % "google-api-services-cloudbilling"         % s"v1-rev20220908-$googleV"
  val googleGenomics: ModuleID =             "com.google.apis"       % "google-api-services-genomics"             % s"v2alpha1-rev20220913-$googleV"
  val googleStorage: ModuleID =              "com.google.apis"       % "google-api-services-storage"              % s"v1-rev20220705-$googleV"
  val googleCloudResourceManager: ModuleID = "com.google.apis"       % "google-api-services-cloudresourcemanager" % s"v1-rev20220828-$googleV"
  val googleAdminDirectory: ModuleID =       "com.google.apis"       % "google-api-services-admin-directory"      % s"directory_v1-rev20220919-$googleV"
  val googleGroupsSettings: ModuleID =       "com.google.apis"       % "google-api-services-groupssettings"       % s"v1-rev20210624-$googleV"
  val googleOAuth2: ModuleID =               "com.google.apis"       % "google-api-services-oauth2"               % s"v2-rev20200213-$googleV"
  val googlePubSub: ModuleID =               "com.google.apis"       % "google-api-services-pubsub"               % s"v1-rev20220904-$googleV"
  val googleServicemanagement: ModuleID =    "com.google.apis"       % "google-api-services-serviceusage"    % s"v1-rev20220907-$googleV"
  val googleIam: ModuleID =                  "com.google.apis"       % "google-api-services-iam"                  % s"v1-rev20220825-$googleV"
  val googleBigQuery: ModuleID =             "com.google.apis"       % "google-api-services-bigquery"             % s"v2-rev20220924-$googleV"
  val googleGuava: ModuleID = "com.google.guava"  % "guava" % "32.1.3-jre"
  val googleRpc: ModuleID =               "io.grpc" % "grpc-core" % "1.58.0"
  val googleRpc2: ModuleID =               "io.grpc" % "grpc-core" % "1.58.0"
  val googleStorageNew: ModuleID = "com.google.cloud" % "google-cloud-storage" % "2.41.0"
  val googleStorageLocal: ModuleID = "com.google.cloud" % "google-cloud-nio" % "0.127.7" % "test"
  val googlePubsubNew: ModuleID = "com.google.cloud" % "google-cloud-pubsub" % "1.125.11"
  val googleKms: ModuleID = "com.google.cloud" % "google-cloud-kms" % "2.33.0"
  val googleComputeNew: ModuleID = "com.google.cloud" % "google-cloud-compute" % "1.40.0"
  val googleDataproc: ModuleID =    "com.google.cloud" % "google-cloud-dataproc" % "4.27.0"
  val googleContainer: ModuleID = "com.google.cloud" % "google-cloud-container" % "2.31.0"
  val kubernetesClient: ModuleID = "io.kubernetes" % "client-java" % "19.0.0"
  val googleBigQueryNew: ModuleID = "com.google.cloud" % "google-cloud-bigquery" % "2.34.1"
  val google2CloudBilling = "com.google.cloud" % "google-cloud-billing" % "2.30.0"
  val googleStorageTransferService: ModuleID = "com.google.cloud" % "google-cloud-storage-transfer" % "1.30.0"
  val googleResourceManager =  "com.google.cloud" % "google-cloud-resourcemanager" % "1.32.0"

  //the below v1 module is a dependency for v2 because it contains the OAuth scopes necessary to created scoped credentials
  val googleContainerV1: ModuleID = "com.google.apis" % "google-api-services-container" % "v1-rev20231024-2.0.0"



  val circeCore: ModuleID = "io.circe" %% "circe-core" % circeVersion
  val circeParser: ModuleID = "io.circe" %% "circe-parser" % circeVersion
  val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % circeVersion % "test"
  val circeFs2: ModuleID = "io.circe" %% "circe-fs2" % "0.14.1"
  val log4cats = "org.typelevel" %% "log4cats-slf4j"   % "2.6.0"
  val catsMtl = "org.typelevel" %% "cats-mtl" % "1.4.0"

  val http4sCirce = "org.http4s" %% "http4s-circe" % http4sVersion
  val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % http4sVersion
  val http4sDsl = "org.http4s"      %% "http4s-dsl"          % http4sVersion

  val fs2Io: ModuleID = "co.fs2" %% "fs2-io" % "3.6.1"
  val rawlsModel: ModuleID = "org.broadinstitute.dsde" %% "rawls-model" % "v0.0.189-SNAP" exclude("com.typesafe.scala-logging", "scala-logging_2.13") exclude("com.typesafe.akka", "akka-stream_2.13")
  val openCensusApi: ModuleID = "io.opencensus" % "opencensus-api" % openCensusV
  val openCensusImpl: ModuleID = "io.opencensus" % "opencensus-impl" % openCensusV
  val openCensusStatsPrometheus: ModuleID = "io.opencensus" % "opencensus-exporter-stats-prometheus" % openCensusV
  val openCensusStatsStackDriver: ModuleID = "io.opencensus" % "opencensus-exporter-stats-stackdriver" % openCensusV
  val openCensusTraceStackDriver: ModuleID = "io.opencensus" % "opencensus-exporter-trace-stackdriver" % openCensusV
  val openCensusTraceLogging: ModuleID = "io.opencensus" % "opencensus-exporter-trace-logging" % openCensusV
  val prometheusServer: ModuleID = "io.prometheus" % "simpleclient_httpserver" % "0.16.0"
  val sealerate: ModuleID = "ca.mrvisser" %% "sealerate" % "0.0.6"
  val scalaCache = "com.github.cb372" %% "scalacache-caffeine" % "1.0.0-M6"

  val swaggerUi = "org.webjars" % "swagger-ui" % "5.17.14"

  val azureResourceManagerCompute = "com.azure.resourcemanager" % "azure-resourcemanager-compute" % "2.32.0" exclude("net.minidev", "json-smart")
  val azureIdentity =  "com.azure" % "azure-identity" % "1.13.0"
  val azureRelay =     "com.azure.resourcemanager" % "azure-resourcemanager-relay" % "1.0.0-beta.2"
  val azureStorageBlob =  "com.azure" % "azure-storage-blob" % "12.24.1"
  val azureResourceManagerContainerService = "com.azure.resourcemanager" % "azure-resourcemanager-containerservice" % "2.32.0"
  val azureResourceManagerApplicationInsights =
    "com.azure.resourcemanager" % "azure-resourcemanager-applicationinsights" % "1.0.0"
  val azureResourceManagerBatchAccount =
    "com.azure.resourcemanager" % "azure-resourcemanager-batch" % "1.0.0"
  val azureServiceBus = "com.azure" % "azure-messaging-servicebus" % "7.17.1"

  // Note: this override can be removed when "io.kubernetes" % "client-java" publishes a new version containing
  // non-vulnerable bouncy castle version. See https://broadworkbench.atlassian.net/browse/WM-2631
  val bouncyCastleOverrides = Seq(
    //Override for bouncy castle to address CVE-2024-30172
    bouncyCastle, bouncyCastleProviderExt, bouncyCastleProvider
  )

  val commonDependencies = Seq(
    jose4j,
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
    "org.typelevel" %% "cats-core" % "2.9.0"
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
    googleAdminDirectory,
    googleGroupsSettings,
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
    catsEffect,
    bouncyCastle,
    bouncyCastleProviderExt,
    bouncyCastleProvider,
    googleRpc2,
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
    googleStorageTransferService,
    googleResourceManager,
    scalaCache,
    scalaTestMockito
  )

  val azureDependencies = List(
    log4cats,
    azureResourceManagerCompute,
    azureIdentity,
    azureRelay,
    azureStorageBlob,
    azureResourceManagerContainerService,
    kubernetesClient,
    azureResourceManagerApplicationInsights,
    azureResourceManagerBatchAccount,
    azureServiceBus,
    scalaTestMockito,
    scalatest,
    circeGeneric,
    awaitility,
    byteBuddy
  ) ++ Seq(
    "net.minidev" % "json-smart" % jsonSmartV
  ) ++ bouncyCastleOverrides

  val openTelemetryDependencies = List(
    catsEffect,
    log4cats,
    openCensusApi,
    openCensusImpl,
    openCensusStatsStackDriver,
    openCensusTraceStackDriver,
    openCensusStatsPrometheus,
    prometheusServer
  )

  val errorReportingDependencies = List(
    catsEffect,
    "com.google.cloud" % "google-cloud-errorreporting" % "0.144.0-beta"
  )

  val util2Dependencies = commonDependencies ++ List(
    catsEffect,
    log4cats,
    fs2Io,
    circeFs2,
    circeCore,
    circeParser,
    circeGeneric,
    catsMtl,
    kubernetesClient
  ) ++ bouncyCastleOverrides

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

  val oauth2Dependencies = commonDependencies ++ Seq(
    http4sCirce,
    http4sBlazeClient,
    http4sDsl,
    swaggerUi,
    // note the following akka dependencies have "provided" scope, meaning the system using
    // this module needs to provide the dependency.
    akkaHttp,
    akkaStream,
    // test dependencies
    akkaHttpTestkit,
    akkaStreamTestkit
  )
}
