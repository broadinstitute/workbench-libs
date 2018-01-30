# Changelog

This file documents changes to the `workbench-google` library, including notes on how to upgrade to new versions.

## 0.12

SBT dependency: `TBD`

### Changed

- Added GoogleStorageDAO
- Added `listUserManagedServiceAccountKeys` to `GoogleIamDAO`
- `HttpGoogleIamDAO` now properly preserves etags to protect against concurrent policy changes

## 0.11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.11-7ad0aa8"`

### Changed

- Added BigQuery

## 0.10

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.10-0967a99"`

### Changed

- Moved GoogleProject to model lib
- Updated for 0.8 version of model 

## 0.9

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.9-dcca21f"`

### Changed

- Reduced amount of config required to use HttpGoogleDirectoryDAO. The old way still is supported but is now deprecated.
- Added listGroupMembers to GoogleDirectoryDAO
- Added createGroup variant the takes just a string instead of WorkbenchGroupName and deprecated the old version

## 0.8

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.8-58a51e7"`

### Added

- Methods on GoogleIamDAO to create/remove service account keys

## 0.7

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.7-af345cd"`

To depend on the `MockGoogle*` classes, additionally depend on:

`"org.broadinstitute.dsde.workbench" %% "workbench-google"  % "0.7-af345cd" % "test" classifier "tests"`

### Fixed

- Fixes finding, creating, and removing service accounts per corrections in `workbench-model v0.5`. 

### Upgrade notes

This version depends on v0.5 of `workbench-model`.

## 0.6

**This version has a serious bug around service accounts, please use 0.7 or higher**

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.6-ecc64be"`

To depend on the `MockGoogle*` classes, additionally depend on:

`"org.broadinstitute.dsde.workbench" %% "workbench-google"  % "0.6-ecc64be" % "test" classifier "tests"`

### Changed

- Mocks moved to the `test` package. 

## 0.5

**This version has a serious bug around service accounts, please use 0.7 or higher**

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.5-b4c9b5c"`

### Changed

- Method signatures in `GoogleIamDAO` to take a `GoogleProject` instead of a `String`

## 0.4

**This version has a serious bug around service accounts, please use 0.7 or higher**

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.4-b23a91c"`

### Added

- GoogleIamDAO for creating service accounts and modifying IAM roles

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.3-0085f3f"`

### Changed

- Inherited changes from workbench-metrics.

### Added

- `gcs` package containing:
   - rich model types for GCS full path, bucket, relative path 
   - ability to parse and validate a GCS path from a string
   - ability to generate a unique valid bucket name given a prefix
- `Dataproc` instrumented service 

## 0.2

### Added

- Instrumentation of Google Cloud HTTP requests, including metrics for retries.

## 0.1

### Added

- This library
- `GoogleUtilities` for talking to Google APIs
- `GooglePubSubDAO` and friends, a DAO for talking to Google PubSub
