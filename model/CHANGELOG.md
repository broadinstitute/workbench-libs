# Changelog

This file documents changes to the `workbench-model` library, including notes on how to upgrade to new versions.

## 0.20

Adds group version and last synchronized version to `WorkbenchGroup` as required fields.

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.20-f7d5217"`

## 0.19

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.19-1c0cf92"`

### Dependency upgrades
| Dependency   | Old Version | New Version |
|----------|:-----------:|------------:|
| guava |  31.1-jre   |  32.1.3-jre |
| sbt-scoverage |    2.0.8    |       2.0.9 |
| scalatest |   3.2.16    |      3.2.17 |
| scala       |   2.13.11   |     2.13.12 |
| sbt-scalafix       |   0.11.0    |      0.11.1 |
| jose4j      |    0.9.3    |       0.9.4 |

## 0.18

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.18-d764a9b"`

## 0.17

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.17-01a11c3"`

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

## 0.16

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.16-e20067a"`

### Added
- `IamModel`, `IamMemberTypes`, and `IamResourceTypes`

## 0.15

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.15-a78f6e9"`

### Added
- AzureB2CId to WorkbenchUser

## Removed
- IdentityConcentratorId

## 0.14

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.14-89d0d9e"`

### Changed
- added IdentityConcentratorId to WorkbenchUser
- Cross build 2.13
- added TraceId to ErrorReport
- added BigQuery dataset and table types
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.14-65bba14"`

### Added

- `IP` data type

## 0.13

- Added generators in `test`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.13-58c913d"`

### Changed

- Remove duplicated `SamModel`

### Added
- TraceId
- GoogleResourceTypes

## 0.12

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.12-3510877"`

### Changed

- Add `googleSubjectId` as a separate field to `WorkbenchUser`

## 0.11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.11-2bddd5b"`

### Added

- `EmailGcsEntity` and `ProjectGcsEntity` as implementations of `GcsEntity`, which is now a `trait`
- `ProjectNumber` to model Google project numbers (distinct from project IDs)
- `ProjectTeamType` ADT to correspond to `entity` in [Google bucket access control resource](https://cloud.google.com/storage/docs/json_api/v1/bucketAccessControls)
- `IamPermission` used by workbench-google `testIamPermission`

### Deprecated

- `GcsEntity` as a `case class`. The former usage defaults to the `EmailGcsEntity` implementation.

## 0.10

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.10-255a466"`

### Added

- Model objects for Google Storage
- `GcsPathParser` (functionality moved from `workbench-google`)

### Changed

- `generateUniqueBucketName` takes optional extra parameter to trim UUID instead of prefix

## 0.9

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.9-d722ae2"`

### Added

- `UserInfo`

## 0.8

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.8-d6801ce"`

### Changed

- Consolidated all Workbench*Email classes into one WorkbenchEmail class
- Created model.google package with all ServiceAccount classes (moved from model)
- Moved GoogleProject from google lib to model.google
- Removed WorkbenchUser prefix from all SerivceAccount model classes

## Added

- PetServiceAccount and PetServiceAccountId

## 0.7

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.7-dcca21f"`

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
