[![Build Status](https://travis-ci.org/broadinstitute/workbench-libs.svg?branch=develop)](https://travis-ci.org/broadinstitute/workbench-libs)

# workbench-libs
Workbench utility libraries, built for Scala 2.11 and 2.12. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

See [GETTING_STARTED.md](GETTING_STARTED.md) for information about making changes to workbench-libs.

See [CONTRIBUTING.md](CONTRIBUTING.md) for information about library versioning.

In this repo:

## workbench-utils

Contains utility functions and classes.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.3-0e9d080"`

[Changelog](util/CHANGELOG.md)

## workbench-model

Contains generic, externally-facing model classes used across Workbench.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.11-2ce3359"`

[Changelog](model/CHANGELOG.md)

NOTE: This library uses akka-http's implementation of spray-json and is therefore not compatible with spray, which you shouldn't be using anyway because it is no longer being maintained.

## workbench-metrics

Contains utilities for instrumenting Scala code and reporting to StatsD using [metrics-scala](https://github.com/erikvanoosten/metrics-scala) and [metrics-statsd](https://github.com/ReadyTalk/metrics-statsd).

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.3-c5b80d2"`

[Changelog](metrics/CHANGELOG.md)

## workbench-google

Contains utility functions for talking to Google APIs and DAOs for Google PubSub, Google Directory, Google IAM, and Google BigQuery. 

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.16-f339f30"`

To depend on the `MockGoogle*` classes, additionally depend on:

`"org.broadinstitute.dsde.workbench" %% "workbench-google"  % "0.16-f339f30" % "test" classifier "tests"`

[Changelog](google/CHANGELOG.md)

## workbench-service-test

Contains common classes and utilities for writing tests against Workbench REST web services.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.10-35e4ee4" % "test" classifier "tests"`

[Changelog](serviceTest/CHANGELOG.md)

## workbench-notifications

Contains utilities for publishing email notifications to PubSub for delivery via SendGrid.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.1-2ce3359"`

[Changelog](notifications/CHANGELOG.md)
