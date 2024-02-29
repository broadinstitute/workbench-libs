# Changelog

This file documents changes to the `workbench-google` library, including notes on how to upgrade to new versions.

## 0.30

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.30-ad61f19"`

Changed:
* Made `GoogleBucketDAO` and `GoogleIamDAO` retry on 412 responses
* Named threads

| Dependency   |     Old Version      |          New Version |
|----------|:--------------------:|---------------------:|
| google-cloud-kms | 2.27.0 | 2.31.0 |
| jackson-module-scala |        2.15.0        |               2.15.3 |
| guava |       32.1.2        |               32.1.3 |

## 0.29

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.29-82d1288"`

### Dependency upgrades
| Dependency   |     Old Version      |          New Version |
|----------|:--------------------:|---------------------:|
| cats-effect |        3.4.11        |                3.5.1 |
| google-api-services-container | v1-rev20230420-2.0.0 | v1-rev20230724-2.0.0 |
| google-cloud-bigquery |        2.25.0        |               2.31.1 |
| google-cloud-nio |       0.126.14       |             0.126.19 |
| google-cloud-resourcemanager |        1.5.6         |               1.25.0 |
| grpc-core |        1.57.2        |               1.58.0 |
| guava |       31.1-jre       |           32.1.2-jre |
| jackson-module-scala |        2.15.0        |               2.15.2 |
| logstash-logback-encoder |         7.3          |                  7.4 |
| sbt-scoverage |        2.0.8         |                2.0.9 |
| scalatest |        3.2.16        |               3.2.17 |
| scala       |       2.13.11        |              2.13.12 |
| sbt-scalafix       |   0.11.0    |               0.11.1 |
| google-cloud-kms          |  2.26.0     |               2.27.0 |

## 0.28

SBT Dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.28-3ad3700"`

Changed:
- Upgraded underlying google libraries to remediate [CVE-2023-32731](https://nvd.nist.gov/vuln/detail/CVE-2023-32731)

## 0.27

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.27-d764a9b"`

## 0.26

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.26-01a11c3"`

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

## 0.25

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.25-e20067a"`

### Changed
- Deprecated `GoogleIamDAO.MemberType`, `GoogleIamDAO.addIamRoles`, and `GoogleIamDAO.removeIamRoles`
- Implemented `GoogleIamDAO.addRoles` and `GoogleIamDAO.removeRoles`
- Pulled Iam Policy models out of `HttpGoogleIamDAO` and into `IamOperations` for code-sharing purposes.

## 0.24

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.24-b63a7db"`

### Changed
- Implemented `addIamRoles` and `removeIamRoles` in `HttpGoogleStorageDAO`.
- Implemented `getBucketPolicy` in `HttpGoogleStorageDAO`.
- In both `HttpGoogleStorageDAO` and `HttpGoogleIamDAO`, the `addIamRoles` method takes an optional `condition` arg. Defaults to `None`.
- Pulled Iam Policy models out of `HttpGoogleIamDAO` and into `IamModel` for code-sharing purposes.
- No code changes should be necessary when updating to this version.
- Google Policy Version 3 for IAM requests to support conditions.

## 0.23

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.23-df84a1e"`

### Changed

- Introduced `MessageRequest` case class in place of using `String` for messages
- Changed `publishMessages` signature to use new `MessageRequest`
- Added `ackDeadlineSeconds` as an optional parameter for `createSubscription`
- Added `attributes` as an optional parameter to `PubSubMessage`

## 0.22

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.22-abd44a6"`

### Changed

- Update `google-api-client` to `2.0.0`
- Removed `google-api-services-plus`, because it is removed from in https://github.com/googleapis/google-api-java-client-services/pull/5947 back in 2020.
The only usage of this library here and in `Rawls` are references to `PlusScopes.USERINFO_EMAIL`, and `PlusScopes.USERINFO_PROFILE`, which can easily
be replaced with `https://www.googleapis.com/auth/userinfo.email`, `https://www.googleapis.com/auth/userinfo.profile` respectively
- Added `GoogleIamDAO.getOrganizationCustomRole`
- Added `GooglePubSubDAO.extendDeadline` and `GooglePubSubDAO.extendDeadlineById`

## 0.21

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-google" % "0.21-c1379f4"`

### Added

- `addIamRoles` and `removeIamRoles` in `GoogleIamDAO`:
  - These methods accept a member type of the newly created ADT `MemberType`.
  - The now deprecated `addIamRolesForUser` and `removeIamRolesForUser` call the aforementioned methods
  for backwards compatibility.
- `getProjectNumber` in `GoogleProjectDAO`
- `addWorkloadIdentityUserRoleForUser` in `GoogleIamDAO`
- `listIamRoles` in `GoogleIamDAO`
- Added enableRequesterPays for workspaces
- Support structured logging for google requests
  - requires dependency "net.logstash.logback" % "logstash-logback-encoder" % "6.6"
- `getProjectName` in `GoogleProjectDAO`
- Added optional parameter `retryIfGroupDoesNotExist` to `addIamRoles` and `removeIamRoles` in `GoogleIamDAO`, which
  will retry if the group does not exist, which could be due to slow Google propogation.


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
- MockGoogleProjectDAO.getAncestry returns 2 elements consistent with reality
- `getQueryStatus` and `getQueryResult` in `HttpGoogleBigQueryDAO` now specify the job location in their request

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
