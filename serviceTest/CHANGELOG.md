# Changelog

This file documents changes to the `workbench-service-test` library, including notes on how to upgrade to new versions.

## 0.8

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.8-TRAVIS-REPLACE-ME"`

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
- with* fixtures preserve original exceptions when cleaning up
- BillingFixures handles roles correctly

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
