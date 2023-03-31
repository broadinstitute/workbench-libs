# Changelog

This file documents changes to the `workbench-openTelemetry` library, including notes on how to upgrade to new versions.

## 0.4

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % "0.4-TRAVIS-REPLACE-ME"`

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

## 0.3
Breaking Changes

- Remove stackdriver stats exporter

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % "0.3-a78f6e9"`

## 0.2
Breaking Changes:
- Upgrade cats-effect to `3.2.3`(see [migration guide](https://typelevel.org/cats-effect/docs/migration-guide#run-the-scalafix-migration)) and a few other dependencies

### Added
- Add `exposeMetricsToPrometheus`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % "0.2-21c84d8"`

## 0.1

### Added
- Add `OpenTelemetryMetrics`
- Add `registerTracing`

### Changed
- Bump `scalatest` to `3.1.1`
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % "0.1-89d0d9e"`
