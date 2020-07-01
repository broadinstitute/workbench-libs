# Changelog

This file documents changes to the `workbench-google2` library, including notes on how to upgrade to new versions.

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
