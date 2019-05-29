# Changelog

This file documents changes to the `workbench-google2` library, including notes on how to upgrade to new versions.

## 0.3

Added
- Add `downloadObject`

Changed
- Add `generation` to `GetMetadataResponse`
- Add `generation` and `metadata` as optional fields for `GoogleStorageService.storeObject`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google2" % "0.3-TRAVIS-REPLACE-ME"`

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
