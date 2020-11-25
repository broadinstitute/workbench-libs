# Changelog

This file documents changes to the `workbench-google2` library, including notes on how to upgrade to new versions.

## 0.17
Add
- Add `list` to `GoogleTopicAdmin`
- Add `GoogleSubscriptionAdmin`

Changed
- Remove `retryConfig` from `PublisherConfig`

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
Update mockito-core to 3.6.28 (#414)
Update guava to 30.0-jre (#390)
Update `io.kubernetes client-java` to `5.0.0` to `10.0.0` (This has some breaking changes if you're using the library's API directly)
```
      
SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.17-TRAVIS-REPLACE-ME"`

## 0.16

Add:
- Add `subscriptionName: Option[ProjectSubscriptionName]`, `deadLetterPolicy: Option[SubscriberDeadLetterPolicy]` and `filter: Option[String]` to `SubscriberConfig`
- Add a `GoogleBigQueryService.resource()` method that accepts the Google project to be billed

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
Update akka-actor, akka-stream, ... to 2.6.10 (#391)
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
Update scalatest to 3.2.3 (#403)
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
