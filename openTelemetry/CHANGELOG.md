# Changelog

This file documents changes to the `workbench-openTelemetry` library, including notes on how to upgrade to new versions.

## 0.2
Breaking Changes:
- Upgrade cats-effect to `3.1.1`(see [migration guide](https://typelevel.org/cats-effect/docs/migration-guide#run-the-scalafix-migration)) and a few other dependencies

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % "0.2-TRAVIS-REPLACE-ME"`

## 0.1

### Added
- Add `OpenTelemetryMetrics`
- Add `registerTracing`

### Changed
- Bump `scalatest` to `3.1.1`
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % "0.1-89d0d9e"`
