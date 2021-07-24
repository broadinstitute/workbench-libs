# Changelog

This file documents changes to the `workbench-util2` library, including notes on how to upgrade to new versions.

## 0.2
Breaking Changes:
- Upgrade cats-effect to `3.1.1` and a few other dependencies
- `Blocker` is no longer needed for `readJsonFileToA`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util2" % "0.2-TRAVIS-REPLACE-ME"`

## 0.1

- Add `ConsoleLogger`
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util2" % "0.1-d7ed6bf"`

Moved a few utilities that depends on `circe`, `fs2` from `util` to `util2`
