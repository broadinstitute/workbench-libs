# Changelog

This file documents changes to the `workbench-azure` library, including notes on how to upgrade to new versions.

## 0.6

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-azure" % "0.6-TRAVIS-REPLACE-ME"`

### Changes
Upgrade azure-resourcemanager-compute from 2.29.0 to 2.30.0
* Changelog https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/resourcemanager/azure-resourcemanager-compute/CHANGELOG.md#2300-2023-08-25

Upgrade azure-resourcemanager-containerservice from 2.29.0 to 2.30.0
* Changelog https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/resourcemanager/azure-resourcemanager-containerservice/CHANGELOG.md#2300-2023-08-25

### Dependency upgrades
| Dependency                             | Old Version | New Version |
|----------------------------------------|:-----------:|------------:|
| azure-resourcemanager-compute          |   2.29.0    |      2.30.0 |
| azure-resourcemanager-containerservice |   2.29.0    |      2.30.0 |
| azure-storage-blob                     |   12.22.3   |     12.23.1 |
| json-smart                             |   2.4.11    |       2.5.0 |
| sbt-scoverage |        2.0.7         |                2.0.8 |
| scalatest                              |   3.2.16    |      3.2.17 |
| scala       |   2.13.11   |     2.13.12 |

## 0.5

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-azure" % "0.5-128901e"`

### Dependency upgrades
| Dependency                             | Old Version | New Version |
|----------------------------------------|:-----------:|------------:|
| azure-resourcemanager-compute          |   2.26.0    |      2.29.0 |
| azure-resourcemanager-containerservice |   2.26.0    |      2.29.0 |
| azure-storage-blob                     |   12.22.0   |     12.22.3 |
| azure-identity                         |    1.7.3    |      1.10.0 |
| client-java                            |   18.0.0    |      18.0.1 |
| json-smart                             |   2.4.10    |      2.4.11 |
| sbt-scoverage                          |    2.0.7    |       2.0.8 |
| scalatest                              |   3.2.15    |      3.2.16 |

### Changed

- AzureContainerServiceInterp getClusterCredentials now gets admin credentials rather than user credentials.

## 0.4

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-azure" % "0.4-63d2d78"`

### Added

- Added `AzureRelayService#getHybridConnectionKey`

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-azure" % "0.3-01a11c3"`

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

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-azure" % "0.2-4b46aac"`

### Changed

- AzureVmService now calls deallocateAsync instead of powerOffAsync when pausing an Azure virtual machine.

### Added

- Added AzureApplicationInsightsService
- Added AzureBatchService

## 0.1

### Added

- Added AzureStorageService and AzureStorageManualTest
- Added AzureContainerService#listClusters

