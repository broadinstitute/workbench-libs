# Changelog

This file documents changes to the `workbench-google` library, including notes on how to upgrade to new versions.

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.3-324fef3"`

### Changed

- Inherited changes from workbench-metrics.

## 0.2

### Added

- Instrumentation of Google Cloud HTTP requests, including metrics for retries.

## 0.1

### Added

- This library
- `GoogleUtilities` for talking to Google APIs
- `GooglePubSubDAO` and friends, a DAO for talking to Google PubSub
