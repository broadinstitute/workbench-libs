# Changelog

This file documents changes to the `workbench-notifications` library, including notes on how to upgrade to new versions.

## 0.8

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.8-78b1597"

- Added notification for when a TDR snapshot access request is submitted

## 0.7

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.7-9254729"

- Added notification for when a TDR snapshot by request is ready for use

### Dependency upgrades
| Dependency         | Old Version | New Version |
|--------------------|:-----------:|------------:|
| bcpkix-jdk18on     |    1.78     |      1.78.1 |
| bcprov-ext-jdk18on |    1.78     |      1.78.1 |
| bcprov-jdk18on     |    1.78     |      1.78.1 |

## 0.6

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.6-1c0cf92"`

### Dependency upgrades
| Dependency   | Old Version | New Version |
|----------|:-----------:|------------:|
| sbt-scoverage |    2.0.8    |       2.0.9 |
| scalatest |   3.2.16    |      3.2.17 |
| scala       |   2.13.11   |     2.13.12 |
| sbt-scalafix       |   0.11.0    |      0.11.1 |
| jose4j      |    0.9.3    |       0.9.4 |

## 0.5

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.5-d764a9b"`

upgrade jose4j

## 0.4

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.4-01a11c3"`

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

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.3-084d25b"`

### Changed
- Moved `SamModel`
- Target java 11

### Added
- Support scala 2.13
- Added notifications for successful, failed, and aborted submissions
- Added notification for inviting unregistered users to billing projects
- Added notification for Azure preview account activation

## 0.2
- upgrade cats to 1.4.0 and scala to 2.12.7
- turn on more scalac options. upgrade scala to 2.12.11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.2-8d718f2"`

## 0.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.1-2ce3359"`

### Added

- This library
