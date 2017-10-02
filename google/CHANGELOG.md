# Changelog

This file documents changes to the `workbench-google` library, including notes on how to upgrade to new versions.

## 0.4

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.3-???????"`

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
