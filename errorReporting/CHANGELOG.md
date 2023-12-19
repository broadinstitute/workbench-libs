# Changelog

This file documents changes to the `workbench-error-reporting` library, including notes on how to upgrade to new versions.

## 0.8

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.8-TRAVIS-REPLACE-ME"`

### Changes
* downgraded cats_effect, and corresponding fs2
* Named threads

### Dependency upgrades
| Dependency                    |     Old Version      |          New Version |
|-------------------------------|:--------------------:|---------------------:|
| cats-effect                   |         3.5.2        |               3.4.11 |
| fs2-io                        |         3.9.3        |                3.6.1 |


## 0.7

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.7-2eac218"`

### Dependency upgrades
| Dependency   | Old Version | New Version |
|----------|:-----------:|------------:|
| cats-effect |   3.4.11    |       3.5.2 |
| sbt-scoverage                          |    2.0.8    |       2.0.9 |
| scalatest                              |   3.2.16    |      3.2.17 |


## 0.6

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.6-026bc90"`

### Dependency upgrades
| Dependency   |     Old Version      |          New Version |
|----------|:--------------------:|---------------------:|
| cats-effect |        3.4.10        |               3.4.11 |
| sbt-scoverage                          |    2.0.7    |       2.0.8 |
| scalatest                              |   3.2.15    |      3.2.16 |

## 0.5

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.5-3ad3700"`

Changed:
- Upgraded underlying google libraries to remediate [CVE-2023-32731](https://nvd.nist.gov/vuln/detail/CVE-2023-32731)

## 0.4

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.4-d764a9b"`

upgrade jose4j

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.3-01a11c3"`

### Dependency upgrades
| Dependency   |      Old Version      |          New Version |
|----------|:-------------:|---------------------:|
| azure-resourcemanager-compute |  2.17.0 |               2.25.0 |
| azure-resourcemanager-containerservice |  2.19.0 |               2.25.0 |
| azure-storage-blob |  12.19.1 |              12.21.1 |
| cats-effect |  3.4.4 |                3.4.8 |
| circe-core |  0.14.3 |               0.14.5 |
| circe-fs2 |  0.14.0 |               0.14.1 |
| client-java |  17.0.0 |               17.0.1 |
| fs2-io |  3.4.0 |                3.6.1 |
| google-api-services-container |  v1-rev20221110-2.0.0 | v1-rev20230304-2.0.0 |
| google-cloud-bigquery |  2.20.0 |               2.20.2 |
| google-cloud-container |  2.10.0 |               2.16.0 |
| google-cloud-dataproc |  4.4.0 |               4.10.0 |
| google-cloud-nio |  0.126.0 |             0.126.10 |
| google-cloud-pubsub |  1.122.2 |              1.123.7 |
| google-cloud-storage |  2.16.0 |               2.20.2 |
| google-cloud-storage-transfer |  1.6.0 |               1.13.0 |
| grpc-core |  1.51.1 |               1.51.3 |
| http4s-circe |  1.0.0-M35 |            1.0.0-M38 |
| jackson-module-scala |  2.14.1 |               2.15.0 |
| logstash-logback-encoder |  7.2 |                  7.3 |
| sbt-scoverage |  2.0.6 |                2.0.7 |
| scalatest |  3.2.14 |               3.2.15 |

## 0.2

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.2-a78f6e9"`

### Breaking changes
- Upgrade cats-effect to `3.2.3`(see [migration guide](https://typelevel.org/cats-effect/docs/migration-guide#run-the-scalafix-migration)) and a few other dependencies

### Dependency upgrades
| Dependency   |      Old Version      |  New Version |
|----------|:-------------:|------:|
| google-cloud-errorreporting |  0.120.42-beta | 0.122.9-beta |


## 0.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.1-89d0d9e"`

### Added
- Add `ErrorReporting`

### Changed
- Target java 11

