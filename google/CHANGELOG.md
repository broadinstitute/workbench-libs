# Changelog

This file documents changes to the `workbench-google` library, including notes on how to upgrade to new versions.

## 0.21

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.21-89d0d9e"`

### Added

- `addIamRoles` and `removeIamRoles` in `GoogleIamDAO`:
  - These methods accept a member type of the newly created ADT `MemberType`.
  - The now deprecated `addIamRolesForUser` and `removeIamRolesForUser` call the aforementioned methods
  for backwards compatibility.
- `getProjectNumber` in `GoogleProjectDAO`
- `addWorkloadIdentityUserRoleForUser` in `GoogleIamDAO`
- Added enableRequesterPays for workspaces
- Support structured logging for google requests
  - requires dependency "net.logstash.logback" % "logstash-logback-encoder" % "6.6"
- `getProjectName` in `GoogleProjectDAO`

### Changed

- `addIamRolesForUser` and `removeIamRolesForUser` in `GoogleIamDAO`:
  - These methods now check whether the policy has changed before updating Google. A `Boolean` is returned
    indicating whether an update was made.
  - These methods now retry 409s, indicating concurrent modifications to the policy. We retry the entire
    read-modify-write operation as recommended by Google.
- Made `RetryPredicates` handle `null`s more safely
- Creating a group sometimes returns a 5xx error code and leaves behind a partially created group which caused problems 
when we retried creation. Changed to delete the partially created group before retrying
- Cross build to scala 2.13
- Fix potential NPE in `HttpGoogleProjectDAO.isBillingActive()`
- Target java 11

## 0.20

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.20-161813d"`

### Added

- A new set of predicates in `GoogleUtilities.RetryPredicates` to define retry conditions.
- Simpler functions `retry` and `retryWithRecover` that compose a list of these predicates when deciding whether to retry.
- `HttpGoogleDirectoryDAO.deleteGroup` retries on rare instances of Google returning `412 Precondition Failed`.
- A new whenGlobalUsageLimited predicate in `GoogleUtilities.RetryPredicates`

### Changed

 - applied whenGlobalUsageLimited to createServiceAccountKey

### Deprecated

- `when500orGoogleError`, `retryWhen500orGoogleError`, and `retryWithRecoverWhen500orGoogleError` in `GoogleUtilities` have been deprecated in favour of the predicate-composing approach above. Code in workbench-libs has been changed to use this approach, preserving the original behaviour.

### Restored

- Non-Stream versions of `GoogleKmsInterpreter` and `GoogleKmsService` have been restored.

## 0.19

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.19-084fa1b"`

- Moved `org.broadinstitute.dsde.workbench.google.GoogleKmsService` to `org.broadinstitute.dsde.workbench.google2.GoogleKmsService`
- Add `getProjectLabels` to `GoogleProjectDAO`

## 0.18

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.18-d4e0782"`

### Changed
- Moved `SamModel`
- add functions to access `DefaultObjectAccessControls` and `BucketAccesscontrols`
- when getting service account treat 403 from google when error message is "Unable to extract resource containers." as service account does not exist see https://console.cloud.google.com/support/cases/detail/17978989?project=broad-dsde-prod
- Add `GoogleKms` service and interpreter
- Add `enableService` function to `GoogleProjectDAO`
- removing member from google group handles 400 error when user does not exist
- Add `getAncestry` to HttpGoogleProjectDAO
- Add `createProject(projectName: String, parentId: String, parentType: GoogleParentResourceType)`

## 0.17
- upgrade cats to 1.4.0 and scala to 2.12.7
- turn on more scalac options. upgrade scala to 2.12.11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.17-8d718f2"`

## 0.16

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.16-eb6af64"`

### Added
- `org.broadinstitute.dsde.workbench.google.GoogleIamDAO#findServiceAccount(serviceAccountProject: GoogleProject, serviceAccountEmail: WorkbenchEmail)`
- Updated createBucket method in `GoogleStorageDAO` to accept reader and owner acls
- Parameterized query support for `GoogleBigQueryDAO`
   - `org.broadinstitute.dsde.workbench.google.GoogleBigQueryDAO#startParameterizedQuery(project: GoogleProject, querySql: String, queryParameters: java.util.List[QueryParameter], parameterMode: String)`
- Added `GoogleProjectDAO`
- Added `testIamPermission` method to `GoogleIamDAO`
- Added optional group settings when creating google groups 
- Added a way to set service account user when constructing credentials via json
- Added `isProjectActive` and `isBillingActive` to `GoogleProjectDAO`
- Added `getBucket` to `GoogleStorageDAO`
   
### Changed
- org.broadinstitute.dsde.workbench.google.mock.MockGoogleIamDAO supports multiple keys for a service account (like the real thing)

## 0.15

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.15-2fc79a3"`

### Changed

- Pluggable Google credential modes
   - All Google DAOs now extend `AbstractHttpGoogleDAO` and take a `GoogleCredentialMode` as a constructor-arg.
   - The `GoogleCredentialMode` specifies how access tokens should be obtained. Current possibilities are:
      - via a pem file (`GoogleCredentialModes.Pem`)
      - via a json file (`GoogleCredentialModes.Json`)
      - via an access token (`GoogleCredentialModes.Token`)
      - via a `GoogleCredential` as a passthrough.
   - This allows for more flexibility in how Google DAOs can be used.
- Updated methods in GoogleStorageDAO:
   - Added `copyObject`
 
## 0.14

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.14-6800f3a"`

### Changed

- Updated GoogleStorageDAO to use model objects from `workbench-model`
- Updated methods in GoogleStorageDAO:
   - Added methods to create/remove bucket/object ACLs
   - Added `bucketExists(GcsBucketName): Future[Boolean]`
   - Added `storeObject` variants which take an `ByteArrayInputStream` or a `File`
   
### Removed

- `org.broadinstitute.dsde.workbench.google.gcs` package (functionality moved to workbench-model)


## 0.13

SBT dependency: `TBD`

### Changed

- Added `setObjectChangePubSubTrigger` to `GoogleStorageDAO`
- Added `setTopicIamPermissions` to `GooglePubSubDAO`

## 0.12

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.12-5913740"`

### Changed

- Added `GoogleStorageDAO`
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
