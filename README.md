[![Build Status](https://travis-ci.org/broadinstitute/workbench-libs.svg?branch=develop)](https://travis-ci.org/broadinstitute/workbench-libs)

# workbench-libs
Workbench utility libraries, built for Scala 2.11 and 2.12. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

See [GETTING_STARTED.md](GETTING_STARTED.md) for information about making changes to workbench-libs.

See [CONTRIBUTING.md](CONTRIBUTING.md) for information about library versioning.

In this repo:

## workbench-utils

Contains utility functions and classes.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.5-e66171c"`

[Changelog](util/CHANGELOG.md)

## workbench-util2

Contains utility functions and classes. Util2 is added because util needs to support 2.11 for `firecloud-orchestration`,
but many libraries start to drop 2.11 support. Util2 doesn't support 2.11.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util2" % "0.1-92fcd96"`

[Changelog](util2/CHANGELOG.md)

## workbench-model

Contains generic, externally-facing model classes used across Workbench.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.14-65bba14"`

[Changelog](model/CHANGELOG.md)

NOTE: This library uses akka-http's implementation of spray-json and is therefore not compatible with spray, which you shouldn't be using anyway because it is no longer being maintained.

## workbench-metrics

Contains utilities for instrumenting Scala code and reporting to StatsD using [metrics-scala](https://github.com/erikvanoosten/metrics-scala) and [metrics-statsd](https://github.com/ReadyTalk/metrics-statsd).

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.5-65bba14"`

[Changelog](metrics/CHANGELOG.md)

## workbench-google

Contains utility functions for talking to Google APIs and DAOs for Google PubSub, Google Directory, Google IAM, and Google BigQuery. 

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.21-TRAVIS-REPLACE-ME"`

To depend on the `MockGoogle*` classes, additionally depend on:

`"org.broadinstitute.dsde.workbench" %% "workbench-google"  % "0.21-TRAVIS-REPLACE-ME" % "test" classifier "tests"`

[Changelog](google/CHANGELOG.md)

## workbench-google2

Contains utility functions for talking to Google APIs via com.google.cloud client library (more recent) via gRPC. 

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.12-65bba14"`

To start the Google PubSub emulator for unit testing:

`docker run --name pubsub-emulator -d -p 8085:8085 -ti google/cloud-sdk:229.0.0 gcloud beta emulators pubsub start --host-port 0.0.0.0:8085`

[Changelog](google2/CHANGELOG.md)

## workbench-newrelic

Contains utility functions for publishing custom metrics to newrelic. 

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-newrelic" % "0.2-e66171c"`

[Changelog](newrelic/CHANGELOG.md)

## workbench-openTelemetry

Contains utility functions for publishing custom metrics using openTelemetry (openCensus and openTracing). 

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % "0.1-e66171c"`

[Changelog](openTelemetry/CHANGELOG.md)

## workbench-error-reporting

Contains utility functions for publishing custom metrics using openTelemetry (openCensus and openTracing). 

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.1-92fcd96"`

[Changelog](errorReporting/CHANGELOG.md)

## workbench-service-test

Contains common classes and utilities for writing tests against Workbench REST web services.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.18-61887ce" % "test" classifier "tests"`

[Changelog](serviceTest/CHANGELOG.md)

## workbench-notifications

Contains utilities for publishing email notifications to PubSub for delivery via SendGrid.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.3-e66171c"`

[Changelog](notifications/CHANGELOG.md)
