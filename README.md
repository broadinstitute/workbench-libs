[![Build Status](https://travis-ci.org/broadinstitute/workbench-libs.svg?branch=develop)](https://travis-ci.org/broadinstitute/workbench-libs)

# workbench-libs
Workbench utility libraries, built for Scala 2.11 and 2.12. You can find the full list of packages at [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/).

In this repo:

## workbench-utils

Contains utility functions and classes.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.2-0b34b93"`

[Changelog](util/CHANGELOG.md)

## workbench-model

Contains generic, externally-facing model classes used across Workbench.

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.9-d722ae2"`

[Changelog](model/CHANGELOG.md)

NOTE: This library uses akka-http's implementation of spray-json and is therefore not compatible with spray, which you shouldn't be using anyway because it is no longer being maintained.

## workbench-metrics

Contains utilities for instrumenting Scala code and reporting to StatsD using [metrics-scala](https://github.com/erikvanoosten/metrics-scala) and [metrics-statsd](https://github.com/ReadyTalk/metrics-statsd).

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.3-1b977d7"`

[Changelog](metrics/CHANGELOG.md)

## workbench-google

Contains utility functions for talking to Google APIs and DAOs for Google PubSub, Google Directory, and Google IAM. 

Latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.10-0967a99"`

To depend on the `MockGoogle*` classes, additionally depend on:

`"org.broadinstitute.dsde.workbench" %% "workbench-google"  % "0.10-d6801ce" % "test" classifier "tests"`

[Changelog](google/CHANGELOG.md)
