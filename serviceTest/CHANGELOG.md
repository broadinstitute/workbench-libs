# Changelog

This file documents changes to the `workbench-service-test` library, including notes on how to upgrade to new versions.

## 2.0
SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "2.0-99b674c"`

### Changed
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
