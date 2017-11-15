# Changelog

This file documents changes to the `workbench-model` library, including notes on how to upgrade to new versions.

## 0.7

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.7-xxxxxxx"`

### Added

- Model objects for pet service account keys
- Support for base 64 encoding/decoding value objects
- JSON formatters for java.time.Instant
- emailOf function on WorkbenchSubject

## 0.6

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.6-e161b68"`

### Changed

- Reorganization of workbench identity class hierarchy to allow different implementation of WorkbenchGroup

### Upgrade notes

- `WorkbenchGroup` has been changed from a case class to a trait.
- `WorbenchSubject` no longer extends ValueObject as not all subjects can be represented by a single value
- Introduced `WorkbenchGroupIdentity` which extends `WorbenchSubject`


## 0.5

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.5-af345cd"`

### Fixed

- Fixed confusion around the `WorkbenchUserServiceAccount` class.

### Upgrade notes

`WorkbenchUserServiceAccountId` was often misconstrued to have two meanings:

1. The service account's unique id (subject id); and
2. The [local-part](https://en.wikipedia.org/wiki/Email_address)(i.e. before the `@`) of the service account's generated email address.

The first of these is now known as `WorkbenchUserServiceAccountUniqueId` and has replaced the second in the definition of `WorkbenchUserServiceAccount`.  
The second has been renamed to `WorkbenchUserServiceAccountName`.

Users are advised to watch for compile errors around `WorkbenchUserServiceAccount`. The `WorkbenchUserServiceAccountId` class no longer exists so errors should be easy to spot.

## 0.4

SBT depdendency:  `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.4-72adc94"`

### Removed
 
- Moved `WorkbenchUserEmail.isServiceAccount` method to the `google` module

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.3-b23a91c"`

### Added

- Model objects for pet service accounts

## 0.2

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.2-c7726ac"`

### Changed

- org.broadinstitute.dsde.workbench.model.WorkbenchGroup: id changed to name, members changed to Set[WorkbenchSubject]

## 0.1

**This version has a serious bug around service accounts, please use 0.2 or higher**

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.1-17b01fe"`

### Added

- This library
- `ErrorReport`

### Upgrade notes

#### ErrorReport

In order to use `ErrorReport` you must define your own `implicit val errorReportSource = ErrorReportSource("yourproject")` and have it in scope when you construct `ErrorReport` instances. Failing to do this can cause incredibly cryptic errors, especially if you're using akka-http code at the time.

#### Moving from Rawls' `workbench-util`

If you're moving from the `workbench-util` published by Rawls, you'll have to do the following things:

- Move imports from `org.broadinstitute.dsde.rawls.model` to `org.broadinstitute.dsde.workbench.model`
- Upgrade from spray to akka-http
