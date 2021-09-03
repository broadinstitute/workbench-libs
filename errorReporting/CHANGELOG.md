# Changelog

This file documents changes to the `workbench-error-reporting` library, including notes on how to upgrade to new versions.

## 0.2
Breaking Changes:
- Upgrade cats-effect to `3.2.3`(see [migration guide](https://typelevel.org/cats-effect/docs/migration-guide#run-the-scalafix-migration)) and a few other dependencies

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.2-TRAVIS-REPLACE-ME"`

## 0.1

### Added
- Add `ErrorReporting`

### Changed
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-error-reporting" % "0.1-89d0d9e"`
