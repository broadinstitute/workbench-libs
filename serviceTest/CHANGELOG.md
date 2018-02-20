# Changelog

This file documents changes to the `workbench-service-test` library, including notes on how to upgrade to new versions.

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.3-3ffeb7010f39b99f1254b9aa25072f45700cc516"`

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
