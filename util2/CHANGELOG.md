# Changelog

This file documents changes to the `workbench-util2` library, including notes on how to upgrade to new versions.

## 0.5

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util2" % "0.5-026bc90"`

### Dependency upgrades
| Dependency   |     Old Version      |          New Version |
|----------|:--------------------:|---------------------:|
| cats-effect |        3.4.10        |               3.4.11 |
| fs2-io |        3.4.0         |                3.6.1 |
| sbt-scoverage |        2.0.7         |                2.0.8 |
| scalatest |        3.2.15        |               3.2.16 |


## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util2" % "0.3-01a11c3"`

### Dependency upgrades
| Dependency   |      Old Version      |  New Version |
|----------|:-------------:|------:|
| azure-resourcemanager-compute |  2.17.0 | 2.25.0 |
| azure-resourcemanager-containerservice |  2.19.0 | 2.25.0 |
| azure-storage-blob |  12.19.1 | 12.21.1 |
| cats-effect |  3.4.4 | 3.4.8 |
| circe-core |  0.14.3 | 0.14.5 |
| circe-fs2 |  0.14.0 | 0.14.1 |
| client-java |  17.0.0 | 17.0.1 |
| fs2-io |  3.4.0 | 3.6.1 |
| google-api-services-container |  v1-rev20221110-2.0.0 | v1-rev20230304-2.0.0 |
| google-cloud-bigquery |  2.20.0 | 2.20.2 |
| google-cloud-container |  2.10.0 | 2.16.0 |
| google-cloud-dataproc |  4.4.0 | 4.10.0 |
| google-cloud-nio |  0.126.0 | 0.126.10 |
| google-cloud-pubsub |  1.122.2 | 1.123.7 |
| google-cloud-storage |  2.16.0 | 2.20.2 |
| google-cloud-storage-transfer |  1.6.0 | 1.13.0 |
| grpc-core |  1.51.1 | 1.51.3 |
| http4s-circe |  1.0.0-M35 | 1.0.0-M38 |
| jackson-module-scala |  2.14.1 | 2.15.0 |
| logstash-logback-encoder |  7.2 | 7.3 |
| sbt-scoverage |  2.0.6 | 2.0.7 |
| scalatest |  3.2.14 | 3.2.16 |

## 0.2
Breaking Changes:
- Upgrade cats-effect to `3.2.3`(see [migration guide](https://typelevel.org/cats-effect/docs/migration-guide#run-the-scalafix-migration)) and a few other dependencies
- `Blocker` is no longer needed for `readJsonFileToA`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util2" % "0.2-a78f6e9"`

## 0.1

- Add `ConsoleLogger`
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util2" % "0.1-d7ed6bf"`

Moved a few utilities that depends on `circe`, `fs2` from `util` to `util2`
