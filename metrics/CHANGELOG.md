# Changelog

This file documents changes to the `workbench-metrics` library, including notes on how to upgrade to new versions.

## 0.6

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.6-TRAVIS-REPLACE-ME"`

### Dependency upgrades
| Dependency   |      Old Version      |  New Version |
|----------|:-------------:|------:|
| azure-resourcemanager-compute |  xxx | 2.25.0 |
| azure-resourcemanager-containerservice |  xxx | 2.25.0 |
| azure-storage-blob |  xxx | 12.21.1 |
| cats-effect |  xxx | 3.4.8 |
| circe-core |  xxx | 0.14.5 |
| circe-fs2 |  xxx | 0.14.1 |
| client-java |  xxx | 17.0.1 |
| fs2-io |  xxx | 3.6.1 |
| google-api-services-container |  xxx | v1-rev20230304-2.0.0 |
| google-cloud-bigquery |  xxx | 2.20.2 |
| google-cloud-container |  xxx | 2.16.0 |
| google-cloud-dataproc |  xxx | 4.10.0 |
| google-cloud-nio |  xxx | 0.126.10 |
| google-cloud-pubsub |  xxx | 1.123.7 |
| google-cloud-storage |  xxx | 2.20.2 |
| google-cloud-storage-transfer |  xxx | 1.13.0 |
| grpc-core |  xxx | 1.51.3 |
| http4s-circe |  xxx | 1.0.0-M39 |
| jackson-module-scala |  xxx | 2.14.2 |
| logstash-logback-encoder |  xxx | 7.3 |
| sbt-scoverage |  xxx | 2.0.7 |
| scalatest |  xxx | 3.2.15 |

## 0.5

### Changed
- Moved `SamModel`
- Publish 2.13
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.5-a78f6e9"`

## 0.4
- upgrade cats to 1.4.0 and scala to 2.12.7
- turn on more scalac options. upgrade scala to 2.11.12

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.4-8d718f2"`

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.3-c5b80d2"`

### Changed

- The `WorkbenchInstrumented.asXXX` functions now return our own metric types so they don't immediately forget their own names.
  - You may need to switch some imports from `nl.grons.metrics.scala` to `org.broadinstitute.dsde.workbench.metrics`.

### Added

- Support for transient metrics (e.g. metrics that get automagically deleted in Hosted Graphite).

## 0.2

### Added

- Added WorkbenchStatsD which filters out detailed Timer metrics from being sent to StatsD.
- Added `ExpandedMetricBuilder.asGaugeIfAbsent` method to handle gauge name conflicts.
- Added InstrumentedRetry trait which updates a histogram with the number of errors in a `RetryableFuture`.

### Changed

- Updated method signature of `org.broadinstitute.dsde.workbench.metrics.startStatsDReporter` to take an API key

## 0.1

### Added

- This library
- `WorkbenchInstrumented`, a mixin trait for instrumenting arbitrary code
- `InstrumentationDirectives.instrumentRequest`, an akka-http directive for instrumenting incoming HTTP requests
