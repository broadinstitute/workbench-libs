[![Build Status](https://github.com/broadinstitute/workbench-libs/workflows/Unit%20tests/badge.svg)](https://github.com/broadinstitute/workbench-libs/actions)
[![codecov](https://codecov.io/gh/broadinstitute/workbench-libs/branch/develop/graph/badge.svg)](https://codecov.io/gh/broadinstitute/workbench-libs)

# workbench-libs

See [GETTING_STARTED.md](GETTING_STARTED.md) for information about making changes to workbench-libs.

See [CONTRIBUTING.md](CONTRIBUTING.md) for information about library versioning.

In this repo:

## workbench-azure

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains utility functions for talking to Azure APIs.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-azure" % "0.6-2eac218"`

[Changelog](azure/CHANGELOG.md)

## workbench-util

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains utility functions and classes.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.10-e761452"`

[Changelog](util/CHANGELOG.md)

## workbench-util2

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains utility functions and classes. Util2 is added because util needs to support 2.11 for `firecloud-orchestration`,
but many libraries start to drop 2.11 support. Util2 doesn't support 2.11.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util2" % "0.7-2eac218"`

[Changelog](util2/CHANGELOG.md)

## workbench-model

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains generic, externally-facing model classes used across Workbench.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.19-e761452"`

[Changelog](model/CHANGELOG.md)

NOTE: This library uses akka-http's implementation of spray-json and is therefore not compatible with spray, which you shouldn't be using anyway because it is no longer being maintained.

## workbench-metrics

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains utilities for instrumenting Scala code and reporting to StatsD using [metrics-scala](https://github.com/erikvanoosten/metrics-scala) and [metrics-statsd](https://github.com/ReadyTalk/metrics-statsd).

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.8-e761452"`

[Changelog](metrics/CHANGELOG.md)

## workbench-google

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains utility functions for talking to Google APIs and DAOs for Google PubSub, Google Directory, Google IAM, and Google BigQuery.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.30-3d9bda9"`

To depend on the `MockGoogle*` classes, additionally depend on:

`"org.broadinstitute.dsde.workbench" %% "workbench-google"  % "0.30-3d9bda9" % "test" classifier "tests"`

[Changelog](google/CHANGELOG.md)

## workbench-google2

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains utility functions for talking to Google APIs via com.google.cloud client library (more recent) via gRPC.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.34-TRAVIS-REPLACE-ME"`

To start the Google PubSub emulator for unit testing:

`docker run --name pubsub-emulator -d -p 8085:8085 -ti google/cloud-sdk:229.0.0 gcloud beta emulators pubsub start --host-port 0.0.0.0:8085`

[Changelog](google2/CHANGELOG.md)

## workbench-openTelemetry

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains utility functions for publishing custom metrics using openTelemetry (openCensus and openTracing).

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % "0.7-2eac218"`

[Changelog](openTelemetry/CHANGELOG.md)

## workbench-error-reporting

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains utility functions for publishing custom metrics using openTelemetry (openCensus and openTracing).

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.7-2eac218"`

[Changelog](errorReporting/CHANGELOG.md)

## workbench-service-test

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains common classes and utilities for writing tests against Workbench REST web services.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "4.1-e761452" % "test" classifier "tests"`

[Changelog](serviceTest/CHANGELOG.md)

## workbench-notifications

Workbench utility libraries, built for 2.13. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

Contains utilities for publishing email notifications to PubSub for delivery via SendGrid.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.6-e761452"`

[Changelog](notifications/CHANGELOG.md)

## workbench-oauth2

Contains utilities for integrating with Google and B2C oauth.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-oauth2" % "0.5-e761452"`

[Changelog](oauth2/CHANGELOG.md)
