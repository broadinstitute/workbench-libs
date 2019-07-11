# Changelog

This file documents changes to the `workbench-google2` library, including notes on how to upgrade to new versions.

## 0.6

Changed
- Support custom storage IAM roles
- Remove `googleProject` parameter from `insertBucket` in `GoogleStorageInterpreter`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.6-TRAVIS-REPLACE-ME"`

## 0.5

Added
- Add `getBlob`, `createObject`

Changed
- Use linebacker for blocking execution context
- Moved `org.broadinstitute.dsde.workbench.google.GoogleKmsService` to `org.broadinstitute.dsde.workbench.google2.GoogleKmsService`
- Add optional generation parameter to `removeObject`
- Add insertBucket, which supports adding bucket labels
- Deprecate `getObject`, `unsafeGetObject`, and add `getBlobBody`, `unsafeGetObjectBody`
- provide `text/plain` as default `objectType` for `storeObject`
- Bump `http4sVersion` to `0.20.3`
- Deprecate `storeObject`, and add `createObject` that returns `Blob`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.5-40c9ae6"`

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
