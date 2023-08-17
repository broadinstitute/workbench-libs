# Changelog

This file documents changes to the `workbench-google2` library, including notes on how to upgrade to new versions.

## 0.31

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.31-3ad3700"`

Changed:
- Upgraded underlying google libraries to remediate [CVE-2023-32731](https://nvd.nist.gov/vuln/detail/CVE-2023-32731)

## 0.30

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.30-15f1721"`

Changed:
- upgraded jose4j
- Add optional IAM policy version parameter when overriding GCS bucket IAM policy

## 0.29
Changed:
- Added ability to set query params in signed urls

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.29-b52b498"`


## 0.28
Changed:
- Upgraded `com.google.cloud:google-cloud-bigquery` to resolve [CVE-2022-45688](https://nvd.nist.gov/vuln/detail/cve-2022-45688)

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.28-6bc90eb"`


## 0.27
Changed:
- Added ability to create signed URLs for GCS Blobs

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.27-1bab754"`

## 0.26

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.26-01a11c3"`

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
| jackson-module-scala |  2.14.1 | 2.15.2 |
| logstash-logback-encoder |  7.2 | 7.3 |
| sbt-scoverage |  2.0.6 | 2.0.7 |
| scalatest |  3.2.14 | 3.2.15 |

## 0.25
Changed:
- GoogleStorageTransferService now returns `TransferOperation` rather than `Operation`
- Added `MockGoogleStorageTransferService` to test sources
- Added `GoogleBigQueryInterpreter.runJob` to allow for running a generic job
- Update `FakeComputeOperationFuture`
- Added `GoogleStorageService.testIamPermissions`
- Added support for enabling Autoclass on GCS buckets

Dependency Upgrades:
|          Dependency           | Old Version | New Version |
|-------------------------------|:-----------:|------------:|
| google-cloud-storage-transfer |    1.2.0    |    1.2.1    |
| google-cloud-container |    2.5.0    |    2.5.2    |

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.25-db348ae"`

## 0.24

Changed:
- `GoogleComputeService[F]`'s APIs are updated for `google-cloud-compute` upgrade
- Remove `ComputePollOperation`
- Drop 2.12 support
- Exposed storage transfer job customization options
- Add `GoogleStorageService.setRequesterPays`
- Change `GoogleStorageService.getBucket` to return `BucketInfo`
- k8s client upgrade (added our own enums since k8s removed theirs)

Dependency Upgrades:
| Dependency   |      Old Version      |  New Version |
|----------|:-------------:|------:|
| google-cloud-compute |   1.4.4.alpha   |   1.9.1 |
| akka |   2.6.18   |   2.6.20 |
| akka-http |   10.2.7   |   10.2.10 |
| com.google.guava |   31.0.1-jre   |   31.1-jre |
| grpc-core |   1.43.2   |   1.44.1 |
| google-cloud-nio |   0.123.25   |   0.123.28 |
| google-cloud-container |   2.3.0   |   2.3.7 |
| google-cloud-firestore | 2.6.2 | 3.1.0 |
| google-cloud-storage-transfer | 0.2.2 | 1.0.5 |
| client-java |   14.0.1   |   15.0.0 |
| google-cloud-resourcemanager |   1.2.0   |  1.2.11 |
| simpleclient_httpserver |   0.12.0   | 0.15.0 |
| log4cats-slf4j |  2.1.1   |  2.3.0 |
| http4s |  1.0.0-M35   |  1.0.0-M33 |

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.24-0096bac"`

## 0.23
Added:
- Support for creating and monitoring Google Cloud Storage Transfer jobs between Cloud Storage buckets.
- Legacy Cloud Storage roles to `GoogleStorageService`
- `GoogleStorageService/removeIamPolicy` to remove the specified roles from the bucket IAM policy.
- Extension method `asStorageRoles` on `Policy` to convert to `StorageRole`s map
- Added options list to all `GoogleStorageInterpreter` functions

Changed:
- Set `goog-compute-%d` threads as daemon so that they won't prevent JVM from shutdown
- Set `goog-publisher-%d` and `goog-subscriber-%d` threads as daemon so that they won't prevent JVM from shutdown
- Set `goog-dataproc-%d` and Set `goog-disk-%d` as daemon threads for dataproc and disk client so that threads used by these 2 clients have more readable names
- Change return type for `GoogleComputeService`'s `modifyInstanceMetadata`, `addInstanceMetadata` and `removeInstanceMetadata` to `Option[Operation]`
- Wait for setMetadata API call to finish before proceeding in `GoogleDataprocService`'s `startCluster`, and `stopCluster` APIs

Dependency Upgrades:
| Dependency   |      Old Version      |  New Version |
|----------|:-------------:|------:|
| logstash-logback-encoder |  6.6 | 7.0 |
| jackson-module-scala |    2.12.4   |   2.13.0 |
| akka |    2.6.16   |   2.6.18 |
| fs2-io |   3.1.3   |   3.1.6 |
| google-cloud-nio |   0.123.10   |   0.123.16 |
| google-cloud-compute |   1.4.2-alpha   |   1.4.4-alpha |
| google-cloud-dataproc |   1.5.4   |   2.2.2 |
| google-cloud-container |   1.5.0   |   2.2.1 |
| google-cloud-bigquery |   1.137.2   |   2.5.1 |
| google-cloud-kms |   1.43.0   |   2.3.0 |
| google-cloud-billing |   2.1.2  |   2.1.3 |
| google-api-services-container |   v1-rev20210617-1.32.1   |  v1-rev20211014-1.32.1 |
| google-cloud-resourcemanager |   0.118.12-alpha   |  1.2.0 |
| mockito-3-4 |   3.2.9.0   |   3.2.10.0 |
| selenium-3-141 |   3.2.9.0   |   3.2.10.0 |
| client-java |   12.0.0   |   14.0.0 |
| cats-effect |   3.3.1   |   3.3.2 |

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.23-11a45ad"`

## 0.22
Breaking Changes:
- Upgrade cats-effect to `3.2.3` (see [migration guide](https://typelevel.org/cats-effect/docs/migration-guide#run-the-scalafix-migration)) and a few other dependencies
- Upgrade `google-cloud-compute` to `1.3.0-alpha`

Changed:
- Use dataproc client's built in stopCluster method instead of rolling our own

Added:
- `GoogleComputeService.setInstanceTags` for setting network tags on GCE instances

Dependency Upgrades:
- `google-cloud-container` to `1.5.0`
- `io.kubernetes` to `12.0.0`
- `cats-effect` to `3.2.5`, `fs2` to `3.1.3`, `http4s` to `1.0.0-M25`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.22-82a345c"`

## 0.21
Breaking Changes:
- Rename `retryGoogleF` and `tracedRetryGoogleF` to `retryF` and `tracedRetryF`

Added:
- Added `getDataset` and `getTable` to `GoogleBigQueryService`
- Added `RetryConfig` parameter to `GoogleDataprocService`. Defaults to `standardRetryConfig`.
- Updated Bouncy Castle transitive dependency
- Added `EndpointConfig` to Dataproc `CreateClusterConfig`. Used for accessing Dataproc web interfaces.

Changed
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.21-bf66e61"`

Dependency Updates:
- Update google-cloud-compute from 0.118.0-alpha to 0.119.8-alpha

## 0.20
Breaking Changes:
- Make `GoogleDataprocService` support multiple regions
- Make `GoogleComputeService.createInstance` and `GoogleDataprocService.createCluster` return `F[Option[Operation]]`
- Rename `retryGoogleF` and `tracedRetryGoogleF` to `retryF` and `tracedRetryF`

Dependency Updates (latest):
- Update google-cloud-nio from 0.122.5 to 0.122.11 (#563) (2 hours ago) <Scala Steward>
- Update jackson-module-scala from 2.12.3 to 2.12.2 (#532) (2 hours ago) <Scala Steward>
- Update client-java from 11.0.0 to 11.0.1 (#546) (2 hours ago) <Scala Steward>
- Update scalatest from 3.2.3 to 3.2.6 (#549) (2 hours ago) <Scala Steward>
- Update google-cloud-firestore from 2.2.1 to 2.2.5 (#562) (2 hours ago) <Scala Steward>
- Update cats-effect from 2.3.3 to 2.4.0 (#569) (82 seconds ago) <Scala Steward>
- Update google-cloud-errorreporting from 0.120.34-beta to 0.120.36-beta (#561) (2 hours ago) <Scala Steward>
- Update akka-http, akka-http-spray-json, ... from 10.2.3 to 10.2.4 (#544) (2 hours ago) <Scala Steward>
- Update google-cloud-kms from 1.40.5 to 1.40.8 (#539) (2 hours ago) <Scala Steward>
- Update google-cloud-billing from 1.1.12 to 1.1.15 (#558) (2 hours ago) <Scala Steward>
- Update google-cloud-storage from 1.113.13 to 1.113.14 (#566) (2 hours ago) <Scala Steward>
- Update scala-logging from 3.9.2 to 3.9.5 (#568) (2 hours ago) <Scala Steward>
- Update log4cats-slf4j
- Update google-cloud-pubsub
- Update google-cloud-bigquery from 1.127.7 to 1.127.11
- Update guava from 30.1-jre to 30.1.1-jre (#567)
- Update google-cloud-container from 1.2.6 to 1.3.0
- Update mockito-3-4 from 3.2.3.0 to 3.2.6.0

Changed:
- Fix a bug in `genZoneName`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.20-52e271f"`

## 0.19
Changed:
- Renamed and added fields in `GoogleDataprocService.CreateClusterConfig` to support creating Dataproc clusters with secondary preemptible workers.
- Changed return type of `GoogleDataprocService.{createCluster, deleteCluster, resizeCluster}`
- Removed `RetryConfig` from `GoogleDataprocService` constructors
- `GoogleComputeInterpreter` now returns none if it encounters a disabled billing project during `getInstance`
- Update `GKEInterpreter.pollOperation` to log each polling call
- Log `traceId` as mdc context in `GoogleSubscriberInterpreter`

Added:
- Added `GoogleDataprocService.startCluster`
- Added `listPersistentVolumeClaims` to `KubernetesService`
- Added `GoogleBillingInterpreter` and `GoogleBillingService`
- Added `createDataset` and `deleteDataset` to `GoogleBigQueryService`
- Added new constructor to `GoogleBigQueryService` that accepts path to credentials JSON
- Added `deletePv` to `KubernetesService`
- Added `namespaceExists` to `KubernetesService`

Dependency Updates:
```
Update akka-actor, akka-stream, ... from 2.6.10 to 2.6.16 (#498) (56 seconds ago) <Scala Steward>
Update google-cloud-nio from 0.122.3 to 0.122.5 (#482) (76 seconds ago) <Scala Steward>
Update google-cloud-resourcemanager from 0.118.7-alpha to 0.118.8-alpha (#497) (2 minutes ago) <Scala Steward>
Update http4s-blaze-client, http4s-circe, ... from 0.21.16 to 0.21.19 (#499) (2 minutes ago) <Scala Steward>
Update sbt from 1.4.6 to 1.4.7 (#500) (2 minutes ago) <Scala Steward>
```

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.19-e0826b1"`

## 0.18
Added:
- `GoogleDataprocInterpreter` can resize clusters and stop cluster VMs.
- `publishNativeOne` to `GooglePublisher[F]`
- optional `location` parameter to `GoogleStorageService.insertBucket`
- `overrideIamPolicy` to `GoogleStorageService`
- Scalacheck generators for more of the Dataproc models

Changed:
- [BREAKING CHANGE] `GoogleDataprocInterpreter` requires a `GoogleComputeService` instance so it can stop and resize Dataproc
  cluster nodes. Note that this is a breaking change for existing `GoogleDataprocInterpreter` clients.
- Remove duplicate logging in mdc and regular log message for google calls. Add `result` field to mdc logging context.

Dependency Upgrades
```
Update akka-http, akka-http-spray-json, ... from 10.2.1 to 10.2.2 (#435)
Update cats-core, cats-effect from 2.2.0 to 2.3.0 (#438)
Update cats-mtl from 1.0.0 to 1.1.0
Update google-api-services-container from v1-rev20201007-1.30.10 to v1-rev20201007-1.31.0 (#426)
Update google-cloud-bigquery from 1.125.0 to 1.125.2 (#427)
Update google-cloud-container from 1.2.0 to 1.2.1 (#428)
Update google-cloud-dataproc from 1.1.7 to 1.1.8 (#429)
Update google-cloud-errorreporting from 0.120.8-beta to 0.120.9-beta (#430)
Update google-cloud-kms from 1.40.2 to 1.40.3 (#431)
Update google-cloud-nio from 0.122.1 to 0.122.3 (#432)
Update google-cloud-pubsub from 1.109.0 to 1.110.0
Update google-cloud-pubsub from 1.110.1 to 1.110.3 (#468) (2 days ago)
Update google-cloud-storage from 1.113.4 to 1.113.5 (#434)
Update google-cloud-storage from 1.113.6 to 1.113.8 (#469) (3 hours ago)
Update grpc-core from 1.33.1 to 1.34.0 (#436)
Update http4s-blaze-client, http4s-circe, ... from 0.21.14 to 0.21.19 (#471) (2 days ago)
Update jackson-module-scala from 2.11.3 to 2.12.2 (#425)
Update jackson-module-scala from 2.12.0 to 2.12.2 (#466) (3 hours ago)
Update mockito-core from 3.6.28 to 3.7.0 (#472) (3 hours ago)
Update sbt-scalafix from 0.9.23 to 0.9.24 (#424)
```

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.18-7fe0192"`

## 0.17
Added:
- `list` to `GoogleTopicAdmin`
- `GoogleSubscriptionAdmin`
- `setNodepoolAutoscaling` and `setNodepoolSize` to `GKEService`

Changed:
- Remove `retryConfig` from `PublisherConfig`
- Update Kubernetes client library
- Format getCluster response (currently it prints out cert, which seems not ideal and noisy)
- print out more useful info for kubernetes error

Dependency Upgrades
```
Update google-cloud-pubsub to 1.109.0 (#409)
Update fs2-io to 2.4.6 (#411)
Update google-cloud-bigquery to 1.125.0 (#381)
Update google-cloud-firestore to 1.35.2 (#385)
Update google-cloud-kms to 1.40.2 (#386)
Update google-cloud-firestore to 2.1.0 (#412)
Update grpc-core to 1.33.1 (#395) (Note: if your project explicitly specify grpc-core version, you need to update it to match this version)
Update metrics4-scala to 4.1.14 (#413)
Update http4s-blaze-client, http4s-circe, ... to 0.21.12 (#415)
Update http4s-blaze-client, http4s-circe, ... to 0.21.19
Update mockito-core to 3.6.28 (#414)
Update guava to 30.0-jre (#390)
Update `io.kubernetes client-java` from `5.0.0` to `10.0.0` (This has some breaking changes if you're using the library's API directly)
```

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.17-1e1f697"`

## 0.16
Added:
- `subscriptionName: Option[ProjectSubscriptionName]`, `deadLetterPolicy: Option[SubscriberDeadLetterPolicy]` and `filter: Option[String]` to `SubscriberConfig`
- `GoogleBigQueryService.resource()` method that accepts the Google project to be billed

Changed:
- Scala Steward:
```
Update mockito-3-4 to 3.2.3.0 (#404)
Update commons-codec to 1.15 (#393)
Update akka-http, akka-http-spray-json, ... to 10.2.1 (#392)
Update mockito-core to 2.28.2 (#399)
Update sbt to 1.4.4 (#400)
Update google-cloud-container to 1.2.0 (#382)
Update google-cloud-errorreporting to 0.120.8-beta (#384)
Update google-api-services-container to v1-rev20201007-1.30.10 (#380)
Update google-cloud-nio to 0.122.1 (#387) (Note: upgrade to this version if your project explicitly specifies version)
Update akka-actor, akka-stream, ... to 2.6.16 (#391)
Update mockito-core to 3.6.0 (#407)
Update opencensus-api, ... to 0.28.2 (#397)
Update log4cats-slf4j to 1.1.1 (#394)
Update google-cloud-storage to 1.113.4 (#389)
Update google-cloud-pubsub to 1.105.1 (#388)
Update google-cloud-dataproc to 0.122.3 (#383)
Update sbt-scalafix to 0.9.23 (#378)
Update scalacheck to 1.15.1 (#401)
Update commons-codec to 20041127.091804 (#406)
Update scalafmt-core to 2.7.5 (#402)
Update http4s-blaze-client, http4s-circe, ... to 0.21.11 (#398)
Update google-cloud-dataproc to 1.1.7 (#408)
Update scalatest to 3.2.6 (#403)
Update fs2-io to 2.4.5 (#379)
```

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.16-42883ed"`

## 0.15
Added:
- Add `FakeGooglePublisher` mock
- Add `publishOne` to `GooglePublisher`

Changed:
- Upgrade `cats-mtl` to `1.0.0`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.15-426a0c2"`

## 0.14
Changed:
- Changes the return types for some methods in `GKEInterpreter` from `F[Operation]` to `F[Option[Operation]]`
- Change the return type for `createDisk` in `GoogleDiskService` to `F[Option[Operation]]`

Added:
- Add GKE objects to /test `Generators`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.14-aed2645"`

## 0.13
Changed:
- `GKEService.createCluster` now uses legacy `com.google.api.services.container` client and model objects
- `KubernetesModels.KubernetesOperationId` now takes `(operationName: String)` instead of `(operation: Operation)`
- Made `GoogleComputeService.getDisk` recover on 404s and return `F[Option[Disk]]`
- `ComputePollOperation.pollHelper` now returns a Poll Error type when the operation fails
- `ComputePollOperation.PollError` added. Takes an operation and returns the HTTP error message.

Add:
- `GoogleStorageService.getBucket`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.13-6f4d8f1"`

## 0.12
Changed:
- Made `GoogleComputeService.detachDisk` recover on 404s and return `Option[Operation]`
- Support 2.13

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.12-65bba14"`

## 0.11
Changed:
- Update `pollOperation` signature
- Fix a bug for `detachDisk` function
- Add `createSecret` to `KubernetesService`
- remove `ApplicativeAsk` implicit param from `KubernetesService` creation
- add deleteNamespace to `KubernetesService`
- added mocks for `GKEService` and `KubernetesService`
- optimized implementation of `GoogleStorageInterpreter.getBlobBody` to fully use streams
- log only result row count for BigQuery queries
- Expose `GoogleComputeService.fromCredential`
- Added max retries to `SubscriberConfig`
- Update `getCluster`, `getInstance`'s logging to cluster's status
- Don't log as error when `getCluster`, `getInstance` returns NotFound
- Return `None` if `instance`, `cluster` or `disk` doesn't exist when trying to `deleteInstance`, `deleteCluster` or `deleteDisk`
- Expose `GoogleDataprocService.fromCredential`

Added:
- Add `detachDisk`
- Add `streamUploadBlob`
- Add `listPodStatus` to `KubernetesService`, returns statuses of all pods belonging to a k8s cluster
- Add `getServiceExternalIp` to `KubernetesService`
- Add more retry logic to `GKEService`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.11-997b116"`

## 0.10
Changed:
- Move `resizeDisk` from `GoogleComputeService` to `GoogleDiskService`
- Rename KubernetesSerializableName extension classes
- Add `getDisk`
- Make `genDiskName` non-empty
- Bump `http4s` version to `0.21.5`, `scalatest` to `3.1.2`, `fs2-io` to `2.4.2`
- Add `autoDeleteDiskDeviceName: Set[DeviceName]` to `deleteInstance` method
- get nodepool returns an option
- Bump `grpc-core` to `1.28.1`
- Bump `com.google.cloud:google-cloud-firestore` to `1.35.0`

Added:
- Add `GoogleDiskService` and `GoogleDiskInterpreter`
- Add `{create,delete}Disk` and `listDisks` to `GoogleDiskService`
- Refactor parameters for Kubernetes service entity
- Add `BigQuery`
- Add Generator for `DiskName`
- Add Kubernetes client APIs for creating service accounts, roles and role bindings

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.10-5c4e637"`

## 0.9
Changed:
- Fix a bug in `GoogleDataprocService` where region is not set properly
- A few minor dependency updates
- Upgrade Google PubSub library to latest, which deprecated ProjectTopicName in many APIs

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.9-8051635"`

## 0.8
Changed:
- Renamed `ClusterName` to `DataprocClusterName`
- `pollOperation` in `GoogleComputeService` now returns `Stream[F, Operation]`
- bug fix in `deleteBucket`
- Don't throw Not Found when listing objects for empty bucket in `deleteBucket`

Added:
- `GKEInterpreter`, `GKEService`, `KubernetesService`, and `KubernetesInterpreter`
- `com.google.cloud` % `google-cloud-container` SBT Dependency
- `com.google.apis` % `google-api-services-container` SBT Dependency
- `io.kubernetes` % `client-java` SBT Dependency
- add `deleteBucket` to `GoogleStorageService`
- add optional `credentials` parameter to `GoogleStorageService.getBlob`
- `{create,get,delete}Nodepool` to `GKEService`
- Add `getClusterInstances` and `getClusterError`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.8-e08439a"`

## 0.7
Changed:
- Renamed `GoogleDataproc` to `GoogleDataprocService`
- Updated `GoogleDataprocService` methods to take a `GoogleProject`
- Added `scalafmt` plugin and formatted the `google2` module
- Upgrade `circe` version to `0.13.0`
- Bump `http4s` version to `0.21.0`
- Bump `cats-effect` version to `2.1.2`
- Bump `scalacheck` version to `1.14.3`
- Bump "io.grpc" % "grpc-core" to `1.28.0`

Added:
- `GoogleComputeService` and `GoogleComputeInterpreter`
- `com.google.cloud" % "google-cloud-compute` SBT dependency

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.7-51bf177"`

## 0.6
Changed
- Bump `fs2-io` to `2.0.1`
- Add optional `blockderBound` to `GoogleStorageService` constructor
- Remove `LineBacker` usage
- Add arguments to `insertBucket`
- Fix `scala.MatchError` from `handleErrorWith`
- Add `delete` function to `GoogleTopicAdmin` trait and implementation
- Use `recoverWith` instead of `onError` which doesn't actually recover the error

Add
- Add `GoogleDataproc` and `GoogleDataprocInterpreter`
- Add `delete` function to `GoogleTopicAdmin` trait and implementation
- Add `publishNative` to `GooglePublisher[F]` so that user can add attributes easily
- Log messages with traceId in `GoogleSubscriberInterpreter`
- Add `io.chrisdavenport.log4cats.StructuredLogger` instead of `io.chrisdavenport.log4cats.Logger`
- Add `org.broadinstitute.dsde.workbench.google2.GoogleStorageService.fromAccessToken`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.6-858f5a0"`

## 0.5

Added
- Add `getBlob`, `createObject`
- Add `insertBucket`, which supports adding bucket labels
- Add `getIamPolicy`
- Add `setBucketLabels`
- Add `listBlobsWithPrefix`
- Add `isRecursive` parameter to `listBlobsWithPrefix` and `listObjectsWithPrefix`
- Add RetryPredicates

Changed
- Use linebacker for blocking execution context
- Moved `org.broadinstitute.dsde.workbench.google.GoogleKmsService` to `org.broadinstitute.dsde.workbench.google2.GoogleKmsService`
- Add optional generation parameter to `removeObject`
- Deprecate `getObject`, `unsafeGetObject`, and add `getBlobBody`, `unsafeGetObjectBody`
- provide `text/plain` as default `objectType` for `storeObject`
- Bump `http4sVersion` to `0.20.3`
- Deprecate `storeObject`, and add `createObject` that returns `Blob`
- Support custom storage IAM roles
- GoogleStorageService retry config defined per function via parameters instead of per service instance

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.5-32be5dd"`

## 0.4

Added
- Add `setBucketPolicyOnly`
- Add `setObjectMetadata`

Changed
- Update Google Cloud Storage client library to 1.77.0

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.4-74860a5"`

## 0.3

Added
- Add `downloadObject`
- Add constructor for creating GoogleStorageService from application default credential

Changed
- Add `generation` to `GetMetadataResponse`
- Add `generation` and `metadata` as optional fields for `GoogleStorageService.storeObject`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.3-e7d949f"`

## 0.2

Added
- Add `GoogleStorageNotificationCreatorInterpreter.getStorageServer`
- Add `GoogleStorageService.createBucketWithAdminRole`
- Add `GoogleStorageInterpreter.getObjectMetadata`

Changed
- Updated a few return type in `GoogleStorageService` to Stream[F, A] since it's easier to convert from Stream to F, but a bit detour if we go the other direction at caller
- Rename `GoogleServiceNotificationCreator` to `GoogleServiceHttp`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.2-2149dba"`

## 0.1

### Added

- Add `GoogleFirestoreService`
- Add `GoogleStorageService`
- Add `GooglePubSub`
- Expose `topicAdminClientResource`
- Add `GoogleTopicAdmin`, `GoogleStorageNotificationCreater
- Add `GoogleStorageService.resource` helper for constructing `GoogleStorageService`
- Add `GoogleStorageService.getObject`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.1-09ee655"`
