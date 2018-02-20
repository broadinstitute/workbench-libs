# Changelog

This file documents changes to the `workbench-service-test` library, including notes on how to upgrade to new versions.

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-service-test" % "0.3-TRAVIS-REPLACE-ME"`

[Alex + Matt: please update this]

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
