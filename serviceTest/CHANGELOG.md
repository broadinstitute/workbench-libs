# Changelog

This file documents changes to the `workbench-service-test` library, including notes on how to upgrade to new versions.

## 5.0

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "5.0-ecad551"`

### Breaking Changes

- Removed the `TrialBillingAccountAuthToken` class
- Removed dependencies on `trialBillingPemFile` and `trialBillingPemFileClientId` keys in conf files

### Non-breaking Changes

- add Sam resource type config API

### Dependency upgrades
- updated `rawls-model` dependency to `v0.0.189-SNAP`

## 4.4

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "4.4-3a8f98c"`

### Changed
- changed Rawls workspace cloning to v2 API (async plus polling)
- updated Jackson dependency to 2.17.1

## 4.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "4.3-c68ef66"`

### Changed
- `CleanUp.runCodeWithCleanup` no longer throws an exception when the cleanup portion fails

| Dependency  |  Old Version  | New Version |
|-------------|:-------------:|------------:|
| jose4j      |     0.9.3     |       0.9.4 |
| rawls-model | 0.1-9de70db23 |   1824ef0eb |

## 4.2

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "4.2-ad61f19"`

### Changed
- changed Rawls workspace deletion to v2 API (async plus polling)

## 4.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "4.1-59e30fb"`

### Dependency upgrades
- updated withTemporaryBillingProject to optionally not cleanup the billing project
- updated `rawls-model` dependency to `0.1-fb0c9691b`

| Dependency   | Old Version | New Version |
|----------|:-----------:|------------:|
| sbt-scoverage |    2.0.8    |       2.0.9 |
| scalatest |   3.2.16    |      3.2.17 |
| scala       |   2.13.11   |     2.13.12 |
| sbt-scalafix       |   0.11.0    |      0.11.1 |
| jackson-module-scala | 2.15.2 | 2.15.3 |

## 4.0
SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "4.0-e42c23c"`

### Changed

Breaking changes:
- Remove formerly deprecated v1 billing endpoints from rawls and firecloud-orchestration test objects

## 3.2

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "3.2-026bc90"`

### Dependency upgrades
| Dependency   |     Old Version      | New Version |
|----------|:--------------------:|------------:|
| jackson-module-scala |        2.15.0        |      2.15.2 |
| sbt-scoverage |        2.0.7         |       2.0.8 |
| scalatest |        3.2.15        |      3.2.16 |

## 3.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "3.1-8934a35"`

### Changed
- updated `rawls-model` dependency to `0.1-9de70db23`

## 3.0

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "3.0-e4c6984"`

### Changed


Breaking changes:
- Remove remaining `GPAlloc` trait

## 2.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "2.1-42c499b"`

### Changed
- updated `rawls-model` dependency to `0.1-dbe3ca9c`
- added the ability to pass request attributes to workspace creation/cloning

### Dependency upgrades
| Dependency   |      Old Version      |  New Version |
|----------|:-------------:|------:|
| azure-resourcemanager-compute |  2.17.0 | 2.25.0 |
| azure-resourcemanager-containerservice |  2.19.0 | 2.25.0 |
| azure-storage-blob |  12.19.1 | 12.21.1 |
| cats-effect |  3.4.4 | 3.4.8 |
| circe-core |  0.14.3 | 0.14.5 |
| circe-fs2 |  0.14.0 | 0.14.1 |
| client-java |  17.0.0 | 17.0.1 |
| fs2-io |  3.4.0 | 3.6.1 |
| google-api-services-container |  v1-rev20221110-2.0.0 | v1-rev20230304-2.0.0 |
| google-cloud-bigquery |  2.20.0 | 2.20.2 |
| google-cloud-container |  2.10.0 | 2.16.0 |
| google-cloud-dataproc |  4.4.0 | 4.10.0 |
| google-cloud-nio |  0.126.0 | 0.126.10 |
| google-cloud-pubsub |  1.122.2 | 1.123.7 |
| google-cloud-storage |  2.16.0 | 2.20.2 |
| google-cloud-storage-transfer |  1.6.0 | 1.13.0 |
| grpc-core |  1.51.1 | 1.51.3 |
| http4s-circe |  1.0.0-M35 | 1.0.0-M38 |
| jackson-module-scala |  2.14.1 | 2.15.0 |
| logstash-logback-encoder |  7.2 | 7.3 |
| sbt-scoverage |  2.0.6 | 2.0.7 |
| scalatest |  3.2.14 | 3.2.16 |

## 2.0

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "2.0-87d7fa7"`

### Changed
- add ability to create rawls billing projects using Azure managed app coordinates
- ensure http response entities are read only once
- add optional param for ignoreEmptyColumns in rawls submission API
- updated `rawls-model` dependency to `0.1-04a7a76b`

Breaking changes:
- The `GPAllocFixtures` and `BillingFixtures` traits has been removed.
- A `BillingFixtures` object has been added; exporting means of creating and using temporary v2
  billing projects.


## 1.1
SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "1.1-73b77c5"`

### Changed
- Drop 2.12 support
- Fix bug in `Rawls.workspaces.updateAttributes` in previous versions for 2.13

## 1.0
SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "1.0-93a9c2b"`

### Changed
- Removed deprecated `BillingFixtures.withBillingProject`
- Removed unused `BillingFixtures.createNewBillingProject`
- Removed `BillingFixtures.withBrandNewBillingProject` because it was only used in 1 test and we no longer want to use
  v1 Create Billing Project APIs to get Google Projects due to Project per Workspace (PPW)

### Changed
- Removed deprecated `BillingFixtures.withBillingProject`
- Removed unused `BillingFixtures.createNewBillingProject`
- Removed `BillingFixtures.withBrandNewBillingProject` because it was only used in 1 test and we no longer want to use
  v1 Create Billing Project APIs to get Google Projects due to Project per Workspace (PPW)

## 0.21

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.21-6155dc8"`

### Changed
- `RestClient` which underlies many of the higher-level service-test methods, has logging changes:
  - It logs the first 500 chars of the request and response, which could potentially include sensitive information.
  - It makes an attempt to mask Google auth token values from its log entries
  - It now logs the request and response at DEBUG level
  - It now omits logging the less-helpful portions of the request and response
- `CleanUp.runCodeWithCleanup`, which underlies `withWorkspace`:
  - Wraps the thrown Exceptions to ensure logging when cleanup fails regardless of whether the test passed or failed
- `RestClient` sendRequest function (to send any request with exponential retries for testing) is now public
- Add `acceptTermsOfService` and `getTermsOfServiceStatus` Orch endpoints
- Add optional `adminEnabled` and `tosAccepted` fields to `UserStatusInfo` and `UserStatusDiagnostics` in `Sam.scala`
- Include error message when billing project creation fails in `Orchestration/createBillingProject`

## 0.20
Changed:
- Reduce Credentials cache expiration period to 50 mins

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.20-f52bf30"`

### Dependency Updates
- Updated `rawls-model` dependency to `0.1-384ab501b` for Project per Workspace changes

## 0.19

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.19-44c8451"`

### Added

- Added `useReferenceDisks` and `memoryRetryMultiplier` parameters to `Rawls.submissions.launchWorkflow` and `Orchestration.submissions.launchWorkflow`
- Add a big more logging in `WebBrowserSpec`

### Dependency Updates
- Updated `rawls-model` dependency to `0.1-90eae81cd`
- `jackson-module-scala` to `2.12.5`

## 0.18
Changed
- Bump selenium version
- Check if log type is available before trying to get the log
- Add a few more helful chrome option
- Supports scala 2.13
- Target java 11

Added
- Rawls billing v2 support
- add optional `bucketLocation` parameter to `WorkspaceFixtures.withWorkspace` and `WorkspaceFixtures.withClonedWorkspace`
- add optional `bucketLocation` parameter to `Orchestration.workspaces.create`
- Add `getBucket` to `Google.storage` object
- Added more Scalatest tag descriptors intended to target tests to specific environments
- Added dataRepoApiUrl to CommonConfig

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.18-89d0d9e"`

## 0.17

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.17-d8123e1"`

###

### Added

- add `deleteIntermedateOutputFiles` parameter to `Rawls.submissions.launchWorkflow`

## 0.16

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.16-20dceaa"`


### Added
- add Rawls billing API functions
- move BillingProject out of Orchestration

### Changed
- automation tests now request the `cloud-billing` scope instead of the `cloud-platform` scope in the `AuthTokenScopes.billingScopes`
variable. This better reflects end-user behavior.
- add `updateAcl` and `updateAttributes` endpoint to Rawls.workspaces
- add `storageCostEstimate` endpoint to Orchestration.workspaces
- `createBillingProject` now optionally takes a service perimeter
- fixed `withBrandNewBillingProject` to create project with legal name

## 0.15

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.15-eb721ee"`

- automation tests will now fail if gpalloc does not have billing projects
- add clone workspace endpoint

### Added
- `SamModel` object to `Sam`
- update for group api changes due to sam phase 4
- Rawls.workspaces.getWorkflowCollectionName

## 0.14
- turn on more scalac options. upgrade scala to 2.11.12
- remove unused parameters

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.14-8d718f2"`

## 0.13
- upgrade cats to 1.4.0 and scala to 2.12.7
- bugfix: `BillingFixtures.claimGPAllocProject` now uses the proper OAuth scopes in the case where GPAlloc did not return
a project and therefore the calling test needs to create one on the fly.

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.13-7a663ed"`

## 0.12

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.12-03980d4"`

### Added
- `Orchestration.methodConfigurations.createDockstoreMethodConfigInWorkspace` to create a method config that references a dockstore method
- `Rawls.submissions.getWorkflowOutputs`
- `Rawls.workspace.getBucketName`

### Changed
- `UserAuthToken` now creates access tokens with a reduced set of OAuth scopes. This behavior now accurately mimics the
access tokens generated by end users during interactive login. However, if your test relied on the previous set of scopes,
your test may fail.

## 0.11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.11-3567f6b"`

### Added
-  Added code to make MethodNameSpace unique
- `BillingFixtures.ClaimedProject.cleanup` with new owner `AuthToken` function and email address
- `BillingFixtures.claimGPAllocProject` with new owner `AuthToken` function and email address
- `BillingFixtures.releaseGPAllocProject` with new owner `AuthToken` function and email address
- `BillingFixutres.withCleanBillingProject` with new owner `AuthToken` function and email address
- `Orchestration.trial.adoptTrialProject`
- `Orchestration.trial.scratchTrialProject`

### Deprecated
- `Orchestration.trial.createTrialProjects`. Use `BillingFixtures.withCleanBillingProject` and
`Orchestration.trial.adoptTrialProject` instead.

## 0.10

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.10-35e4ee4"`

### Changed
- split the Config object into the CommonConfig trait and the ServiceTestConfig object.
automation-test repos which need to access Config values should extend CommonConfig.
- moved two fields in Orchestration's ObjectMetadata from String to Option[String], mirroring the same change in
the orchestration codebase.

## 0.9

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.9-b8d7804"`

### Changed
- with* fixtures preserve original exceptions when cleaning up
- BillingFixures handles roles correctly
- Revamped NIH JWTs: there is now a valid key with access to TCGA only, TARGET only, both, and none
- Added NIH endpoints for syncing the whitelist and getting user's NIH link status
- Added function for Orchestration duos/researchPurposeQuery endpoint
- Added URI encoding to RestClient
- Froze the GATK Docker image version in SubWorkflowFixtures.topLevelMethodConfiguration

## 0.8

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.8-023db6d"`

### Removed
- Config values Project.default and Project.common.  It's now necessary to use BillingFixtures to choose a project.

### Added
- Sam endpoint to get pet access token
- RandomUtil uuidWithPrefix() and randomIdWithPrefix()
- SubWorkflowFixtures for generating a tree of Methods

### Deprecated
- Util makeRandomId() and makeUuid().  Use RandomUtil makeRandomId() and randomUuid() instead.

### Changed
- `Credentials.makeAuthToken` user oAuth2 token are cached, expire automatically after 3600 seconds

## 0.7

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.7-847c3ff"`

### Removed
- petName from services.Sam

### Changed
- GroupFixtures.groupNameToEmail requires implicit auth token param

### Added
- Orchestration.groups.getGroup

## 0.6

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.6-52d614b"`

### Changed

* `org.broadinstitute.dsde.workbench.service.test.CleanUp` now throws exceptions if any cleanup function fails. Cleanup functions where failure is tollerable should be wrapped in a Try-recover.
* rawls api deleteBillingProject requires ownerInfo
* Added a case to retry when receiving 401 from Google after calling AuthToken.makeToken()
* Fixtures no longer depend on WebBrowserSpec
* Rawls: add launchWorkflow, getSubmissionStatus, getWorkflowMetadata, abortSubmission

## 0.5

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.5-c7e4984"`

### Updated

* `withCleanBillingProject` moved to `BillingFixtures` and de-cluttered now that we have a Rawls project unregister.
* Added `claimGPAllocProject` and `releaseGPAllocProject`

## 0.4

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.4-d072389"`

### Added

* `GPAllocFixtures` added, contains `withCleanBillingProject` function to acquire a billing project from GPAlloc instead of making one manually. For more information, see [here](https://github.com/broadinstitute/gpalloc/blob/develop/USAGE.md).

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.3-728e074"`

- Billing project creation will now fail-fast. If an error state is reached before the specific timeout (currently 20 mins), the test will immediately fail.
- BillingFixtures method `withBillingProject` will now take a List of emails as an optional parameter that will add users to Billing project.

## 0.2

** Alpha version, subject to iteration and revision **

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.2-8e9ac2a"`

- Updated to work with the new `AbstractHttpGoogleDAO` in workbench-libs/google version 0.15.
    - `googleBigQueryDAO` constructor takes an AuthToken as a parameter. This will require at least workbench-libs/google v 0.16-8e9ac2a.

## 0.1

** Alpha version, subject to iteration and revision **

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.1-e6d94b3"`

### Added

Initial skeleton with a largely unmodified and untested `RestClient` (from `FireCloudClient` in firecloud-ui).
